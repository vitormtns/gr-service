package com.gerenciadorrural.modules.identity.infrastructure;

import com.gerenciadorrural.infrastructure.database.PostgresTestEnvironment;
import com.gerenciadorrural.infrastructure.database.SpringPostgresTestSupport;
import com.gerenciadorrural.modules.identity.application.InternalUserDeactivatedException;
import com.gerenciadorrural.modules.identity.application.InternalUserSuspendedException;
import com.gerenciadorrural.modules.identity.application.SynchronizeAuthenticatedUser;
import com.gerenciadorrural.modules.identity.domain.InternalUser;
import com.gerenciadorrural.modules.identity.domain.InternalUserRepository;
import com.gerenciadorrural.modules.identity.domain.InternalUserStatus;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class JdbcInternalUserRepositoryIntegrationTest extends SpringPostgresTestSupport {

    @Autowired
    private InternalUserRepository repository;

    @Autowired
    private SynchronizeAuthenticatedUser synchronize;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearDatabase() throws SQLException {
        PostgresTestEnvironment.clearUsers();
    }

    @Test
    void insertsAndReadsNullableIdentityUsingTheExternalUuid() {
        UUID id = UUID.randomUUID();
        InternalUser user = user(id, Optional.empty(), Optional.empty(), InternalUserStatus.ACTIVE, 0);

        Boolean inserted = transactionTemplate.execute(status -> repository.insert(user));
        InternalUser stored = transactionTemplate.execute(status -> repository.findById(id).orElseThrow());

        assertThat(inserted).isTrue();
        assertThat(stored.id()).isEqualTo(id);
        assertThat(stored.email()).isEmpty();
        assertThat(stored.displayName()).isEmpty();
    }

    @Test
    void duplicateInsertReturnsFalseWithoutHidingOtherConstraints() {
        InternalUser user = user(UUID.randomUUID(), Optional.empty(), Optional.empty(), InternalUserStatus.ACTIVE, 0);
        transactionTemplate.executeWithoutResult(status -> assertThat(repository.insert(user)).isTrue());

        transactionTemplate.executeWithoutResult(status -> assertThat(repository.insert(user)).isFalse());
    }

    @Test
    void updateRequiresTheExpectedVersion() {
        InternalUser user = user(UUID.randomUUID(), Optional.of("old@example.test"), Optional.empty(),
                InternalUserStatus.ACTIVE, 0);
        transactionTemplate.executeWithoutResult(status -> repository.insert(user));

        Optional<InternalUser> wrongVersion = transactionTemplate.execute(status -> repository.updateIdentity(
                user.id(), Optional.of("wrong@example.test"), Optional.empty(), Instant.now(), 7));
        Optional<InternalUser> updated = transactionTemplate.execute(status -> repository.updateIdentity(
                user.id(), Optional.of("new@example.test"), Optional.empty(), Instant.now(), 0));

        assertThat(wrongVersion).isEmpty();
        assertThat(updated).get().extracting(InternalUser::version).isEqualTo(1L);
        assertThat(updated).get().extracting(InternalUser::email).isEqualTo(Optional.of("new@example.test"));
    }

    @Test
    void idempotentSynchronizationPreservesVersionAndUpdatedAt() {
        AuthenticatedUser authenticated = authenticated(UUID.randomUUID(), Optional.of("person@example.test"));

        InternalUser first = synchronize.execute(authenticated);
        InternalUser second = synchronize.execute(authenticated);

        assertThat(second.version()).isZero();
        assertThat(second.updatedAt()).isEqualTo(first.updatedAt());
    }

    @Test
    void concurrentFirstSynchronizationCreatesExactlyOneUser() throws Exception {
        AuthenticatedUser authenticated = authenticated(UUID.randomUUID(), Optional.of("person@example.test"));
        List<InternalUser> results = runConcurrently(
                () -> synchronize.execute(authenticated),
                () -> synchronize.execute(authenticated));

        assertThat(results).allMatch(user -> user.id().equals(authenticated.userId()));
        assertThat(results).allMatch(user -> user.version() == 0);
        assertThat(countUsers(authenticated.userId())).isOne();
    }

    @Test
    void concurrentUpdatesUseOptimisticLockingWithoutLostWrites() throws Exception {
        UUID id = UUID.randomUUID();
        synchronize.execute(authenticated(id, Optional.of("initial@example.test")));

        List<InternalUser> results = runConcurrently(
                () -> synchronize.execute(authenticated(id, Optional.of("portal@example.test"))),
                () -> synchronize.execute(authenticated(id, Optional.of("mobile@example.test"))));
        InternalUser stored = transactionTemplate.execute(status -> repository.findById(id).orElseThrow());

        assertThat(results).hasSize(2);
        assertThat(stored.version()).isEqualTo(2);
        assertThat(stored.email()).hasValueSatisfying(email ->
                assertThat(email).isIn("portal@example.test", "mobile@example.test"));
    }

    @Test
    void blockedStatusesRemainUnchanged() throws SQLException {
        UUID suspendedId = insertAsAdmin(InternalUserStatus.SUSPENDED);
        UUID deactivatedId = insertAsAdmin(InternalUserStatus.DEACTIVATED);

        assertThatThrownBy(() -> synchronize.execute(authenticated(suspendedId, Optional.of("new@example.test"))))
                .isInstanceOf(InternalUserSuspendedException.class);
        assertThatThrownBy(() -> synchronize.execute(authenticated(deactivatedId, Optional.of("new@example.test"))))
                .isInstanceOf(InternalUserDeactivatedException.class);
        assertThat(status(suspendedId)).isEqualTo("SUSPENDED");
        assertThat(status(deactivatedId)).isEqualTo("DEACTIVATED");
    }

    @Test
    void runtimeLoginHasNoDirectTablePrivilegesAndAssumesRestrictedRolePerTransaction() throws SQLException {
        try (Connection connection = PostgresTestEnvironment.runtimeConnection();
             Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("select count(*) from app.users"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            connection.setAutoCommit(false);
            statement.execute("set local role app_api");
            try (ResultSet result = statement.executeQuery("select current_user")) {
                result.next();
                assertThat(result.getString(1)).isEqualTo("app_api");
            }
            connection.rollback();
        }
    }

    @Test
    void runtimeRolesCannotBypassRlsAndNoBusinessTableExistsInPublic() throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             Statement statement = connection.createStatement();
             ResultSet roles = statement.executeQuery("""
                     select bool_or(rolbypassrls or rolsuper)
                     from pg_roles
                     where rolname in ('app_api', 'app_test_runtime')
                     """)) {
            roles.next();
            assertThat(roles.getBoolean(1)).isFalse();
        }
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("""
                     select count(*) from information_schema.tables
                     where table_schema = 'public'
                       and table_name in ('users', 'organizations', 'farms',
                                          'organization_memberships', 'membership_farm_scopes')
                     """)) {
            tables.next();
            assertThat(tables.getLong(1)).isZero();
        }
    }

    private static <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private static InternalUser user(
            UUID id,
            Optional<String> email,
            Optional<String> displayName,
            InternalUserStatus status,
            long version
    ) {
        Instant now = Instant.now();
        return new InternalUser(id, email, displayName, status, now, now, version);
    }

    private static AuthenticatedUser authenticated(UUID id, Optional<String> email) {
        Instant now = Instant.now();
        return new AuthenticatedUser(id, email, Optional.empty(), Optional.of("aal1"),
                now.minusSeconds(5), now.plusSeconds(300));
    }

    private static UUID insertAsAdmin(InternalUserStatus status) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("insert into app.users (id, status) values (?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, status.name());
            statement.executeUpdate();
        }
        return id;
    }

    private static long countUsers(UUID id) throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("select count(*) from app.users where id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static String status(UUID id) throws SQLException {
        try (Connection connection = PostgresTestEnvironment.adminConnection();
             var statement = connection.prepareStatement("select status from app.users where id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
