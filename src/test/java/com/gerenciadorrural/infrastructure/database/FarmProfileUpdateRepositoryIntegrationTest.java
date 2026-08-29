package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.farms.domain.FarmProfile;
import com.gerenciadorrural.modules.farms.domain.FarmProfileUpdateResult;
import com.gerenciadorrural.modules.farms.infrastructure.JdbcFarmProfileQueryRepository;
import com.gerenciadorrural.shared.infrastructure.database.DatabaseAccessProperties;
import com.gerenciadorrural.shared.infrastructure.database.SpringTenantTransactionExecutor;
import com.gerenciadorrural.shared.infrastructure.database.TransactionalDatabaseRole;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarmProfileUpdateRepositoryIntegrationTest extends PostgresMigrationTestSupport {

    private static final OffsetDateTime INITIAL_UPDATED_AT = OffsetDateTime.of(2001, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private HikariDataSource dataSource;
    private SpringTenantTransactionExecutor transactions;
    private JdbcFarmProfileQueryRepository repository;
    private UUID userA;
    private UUID userB;
    private UUID tenantA;
    private UUID tenantB;
    private UUID farmA1;
    private UUID farmA2;
    private UUID farmA3;
    private UUID farmB1;

    @BeforeEach
    void setUp() throws Exception {
        createRuntimeRole();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername("farm_profile_update_runtime");
        config.setPassword("farm-profile-update-test");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        dataSource = new HikariDataSource(config);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        transactions = new SpringTenantTransactionExecutor(
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionalDatabaseRole(jdbc, new DatabaseAccessProperties("app", "app_api"))
        );
        repository = new JdbcFarmProfileQueryRepository(new NamedParameterJdbcTemplate(dataSource));
        createFixtures();
    }

    @AfterEach
    void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void updatesOnlyAnActiveFarmInItsTenantAndClassifiesMissesInsideTheTenantTransaction() {
        FarmProfileUpdateResult first = update(contextA(farmA1), farmA1, "Nome atualizado", 0);
        assertThat(first).isInstanceOfSatisfying(FarmProfileUpdateResult.Updated.class, updated -> {
            assertThat(updated.profile().name()).isEqualTo("Nome atualizado");
            assertThat(updated.profile().version()).isEqualTo(1);
        });
        assertFarm(farmA1, "Nome atualizado", 1, true);

        assertThat(update(contextA(farmA1), farmA1, "Segunda atualização", 1))
                .isInstanceOf(FarmProfileUpdateResult.Updated.class);
        OffsetDateTime beforeConflict = updatedAt(farmA1);
        FarmProfileUpdateResult conflict = update(contextA(farmA1), farmA1, "Não persiste", 0);
        assertThat(conflict).isInstanceOf(FarmProfileUpdateResult.VersionConflict.class);
        assertFarm(farmA1, "Segunda atualização", 2, true);
        assertThat(updatedAt(farmA1)).isEqualTo(beforeConflict);

        assertThat(update(contextA(farmB1), farmB1, "Outro tenant", 0))
                .isInstanceOf(FarmProfileUpdateResult.NotAvailable.class);
        assertThat(update(contextB(farmA1), farmA1, "Outro tenant", 2))
                .isInstanceOf(FarmProfileUpdateResult.NotAvailable.class);
        assertThat(update(contextA(farmA2), farmA2, "Inativa", 0))
                .isInstanceOf(FarmProfileUpdateResult.NotAvailable.class);
        assertThat(update(contextA(farmA3), farmA3, "Arquivada", 0))
                .isInstanceOf(FarmProfileUpdateResult.NotAvailable.class);
        assertThat(update(contextA(UUID.randomUUID()), UUID.randomUUID(), "Ausente", 0))
                .isInstanceOf(FarmProfileUpdateResult.NotAvailable.class);
        assertFarm(farmB1, "B1", 0, false);
    }

    @Test
    void usesApplicationRoleAndSettingsOnlyInsideTransactionAndRlsPreventsDirectRuntimeWrites() throws Exception {
        transactions.execute(contextA(farmA1), () -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("app_api");
            assertThat(jdbc.queryForObject("select app.current_user_id()", UUID.class)).isEqualTo(userA);
            assertThat(jdbc.queryForObject("select app.current_tenant_id()", UUID.class)).isEqualTo(tenantA);
            int before = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            FarmProfileUpdateResult result = repository.updateName(new TenantId(tenantA), farmA1, "Mesmo PID", 0);
            int after = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            assertThat(result).isInstanceOf(FarmProfileUpdateResult.Updated.class);
            assertThat(after).isEqualTo(before);
            return null;
        });
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ResultSet settings = statement.executeQuery("select current_user, current_setting('app.current_user_id', true), current_setting('app.current_tenant_id', true)");
            settings.next();
            assertThat(settings.getString(1)).isEqualTo("farm_profile_update_runtime");
            assertThat(settings.getString(2)).isNullOrEmpty();
            assertThat(settings.getString(3)).isNullOrEmpty();
            assertThatThrownBy(() -> statement.executeUpdate("update app.farms set name = 'direto' where id = '" + farmA1 + "'"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            ResultSet roles = statement.executeQuery("select rolbypassrls from pg_roles where rolname = 'app_api'");
            roles.next();
            assertThat(roles.getBoolean(1)).isFalse();
            ResultSet table = statement.executeQuery("select relrowsecurity, relforcerowsecurity from pg_class where oid = 'app.farms'::regclass");
            table.next();
            assertThat(table.getBoolean(1)).isTrue();
            assertThat(table.getBoolean(2)).isTrue();
        }
    }

    @Test
    void rollsBackTheUpdateAndRestoresTheConnectionState() throws Exception {
        assertThatThrownBy(() -> transactions.execute(contextA(farmA1), () -> {
            assertThat(repository.updateName(new TenantId(tenantA), farmA1, "Deve reverter", 0))
                    .isInstanceOf(FarmProfileUpdateResult.Updated.class);
            throw new IllegalStateException("forçar rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertFarm(farmA1, "A1", 0, false);
        assertThat(updatedAt(farmA1)).isEqualTo(INITIAL_UPDATED_AT);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ResultSet settings = statement.executeQuery("select current_user, current_setting('app.current_user_id', true), current_setting('app.current_tenant_id', true)");
            settings.next();
            assertThat(settings.getString(1)).isEqualTo("farm_profile_update_runtime");
            assertThat(settings.getString(2)).isNullOrEmpty();
            assertThat(settings.getString(3)).isNullOrEmpty();
        }
    }

    @Test
    void concurrentUpdatesOfTheSameVersionProduceOneWinnerAndOneConflict() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Attempt> one = executor.submit(concurrentAttempt(contextA(farmA1), "Primeiro", barrier));
            Future<Attempt> two = executor.submit(concurrentAttempt(contextA(farmA1), "Segundo", barrier));
            List<Attempt> attempts = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));

            assertThat(attempts).extracting(Attempt::pid).doesNotHaveDuplicates();
            assertThat(attempts).filteredOn(attempt -> attempt.result() instanceof FarmProfileUpdateResult.Updated).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> attempt.result() instanceof FarmProfileUpdateResult.VersionConflict).hasSize(1);
            assertThat(attempts).noneMatch(attempt -> attempt.result() instanceof FarmProfileUpdateResult.NotAvailable);
            Attempt winner = attempts.stream().filter(attempt -> attempt.result() instanceof FarmProfileUpdateResult.Updated).findFirst().orElseThrow();
            assertFarm(farmA1, winner.name(), 1, true);
        }
    }

    @Test
    void concurrentUpdatesInDifferentTenantsRemainIsolated() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Attempt> a = executor.submit(concurrentAttempt(contextA(farmA1), "A isolada", barrier));
            Future<Attempt> b = executor.submit(concurrentAttempt(contextB(farmB1), "B isolada", barrier));
            Attempt attemptA = a.get(15, TimeUnit.SECONDS);
            Attempt attemptB = b.get(15, TimeUnit.SECONDS);
            assertThat(attemptA.result()).isInstanceOf(FarmProfileUpdateResult.Updated.class);
            assertThat(attemptB.result()).isInstanceOf(FarmProfileUpdateResult.Updated.class);
            assertThat(attemptA.pid()).isNotEqualTo(attemptB.pid());
            assertFarm(farmA1, "A isolada", 1, true);
            assertFarm(farmB1, "B isolada", 1, true);
        }
    }

    private Callable<Attempt> concurrentAttempt(TenantContext context, String name, CyclicBarrier barrier) {
        return () -> transactions.execute(context, () -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            int pid = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            assertThat(jdbc.queryForObject("select app.current_user_id()", UUID.class)).isEqualTo(context.userId());
            assertThat(jdbc.queryForObject("select app.current_tenant_id()", UUID.class)).isEqualTo(context.tenantId().value());
            await(barrier);
            return new Attempt(name, pid, repository.updateName(context.tenantId(), context.farmId(), name, 0));
        });
    }

    private FarmProfileUpdateResult update(TenantContext context, UUID farmId, String name, long expectedVersion) {
        return transactions.execute(context, () -> repository.updateName(context.tenantId(), farmId, name, expectedVersion));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("As operações concorrentes não se encontraram", exception);
        }
    }

    private void createRuntimeRole() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'farm_profile_update_runtime') then create role farm_profile_update_runtime login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls password 'farm-profile-update-test'; end if; grant app_api to farm_profile_update_runtime; end $$");
        }
    }

    private void createFixtures() throws Exception {
        userA = UUID.randomUUID(); userB = UUID.randomUUID(); tenantA = UUID.randomUUID(); tenantB = UUID.randomUUID();
        farmA1 = UUID.randomUUID(); farmA2 = UUID.randomUUID(); farmA3 = UUID.randomUUID(); farmB1 = UUID.randomUUID();
        executeAsAdmin("insert into app.users(id, status) values (?, 'ACTIVE'), (?, 'ACTIVE')", userA, userB);
        executeAsAdmin("insert into app.organizations(id, name, status) values (?, 'Tenant A', 'ACTIVE'), (?, 'Tenant B', 'ACTIVE')", tenantA, tenantB);
        farm(tenantA, farmA1, "A1", "ACTIVE"); farm(tenantA, farmA2, "A2", "INACTIVE"); farm(tenantA, farmA3, "A3", "ARCHIVED"); farm(tenantB, farmB1, "B1", "ACTIVE");
    }

    private void farm(UUID tenant, UUID farm, String name, String status) throws Exception {
        executeAsAdmin("insert into app.farms(id, tenant_id, name, status, version, updated_at) values (?, ?, ?, ?, 0, ?)", farm, tenant, name, status, INITIAL_UPDATED_AT);
    }

    private void assertFarm(UUID farm, String expectedName, long expectedVersion, boolean updated) {
        try (Connection connection = adminConnection(); var statement = connection.prepareStatement("select name, version, updated_at from app.farms where id = ?")) {
            statement.setObject(1, farm);
            ResultSet result = statement.executeQuery(); result.next();
            assertThat(result.getString("name")).isEqualTo(expectedName);
            assertThat(result.getLong("version")).isEqualTo(expectedVersion);
            if (updated) assertThat(result.getObject("updated_at", OffsetDateTime.class)).isAfter(INITIAL_UPDATED_AT);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private OffsetDateTime updatedAt(UUID farm) {
        try (Connection connection = adminConnection(); var statement = connection.prepareStatement("select updated_at from app.farms where id = ?")) {
            statement.setObject(1, farm); ResultSet result = statement.executeQuery(); result.next();
            return result.getObject(1, OffsetDateTime.class);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private TenantContext contextA(UUID farm) { return context(tenantA, userA, farm); }
    private TenantContext contextB(UUID farm) { return context(tenantB, userB, farm); }
    private static TenantContext context(UUID tenant, UUID user, UUID farm) { return new TenantContext(new TenantId(tenant), user, farm, UUID.randomUUID(), "OWNER", "ALL_FARMS"); }
    private record Attempt(String name, int pid, FarmProfileUpdateResult result) { }
}
