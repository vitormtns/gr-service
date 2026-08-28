package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.shared.infrastructure.database.DatabaseAccessProperties;
import com.gerenciadorrural.shared.infrastructure.database.SpringTenantTransactionExecutor;
import com.gerenciadorrural.shared.infrastructure.database.TransactionalDatabaseRole;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionContextConflictException;
import com.gerenciadorrural.shared.tenancy.TenantTransactionInfrastructureException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantTransactionExecutorIntegrationTest extends PostgresMigrationTestSupport {

    private static final String ROLE = "tenant_tx_runtime";
    private static final String PASSWORD = "tenant-tx-test";

    private HikariDataSource dataSource;
    private SpringTenantTransactionExecutor executor;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    do $$
                    begin
                        if not exists(select 1 from pg_roles where rolname = 'tenant_tx_runtime') then
                            create role tenant_tx_runtime login noinherit nosuperuser nocreatedb nocreaterole
                                noreplication nobypassrls password 'tenant-tx-test';
                        end if;
                        grant app_api to tenant_tx_runtime;
                    end
                    $$
                    """);
        }
        configureExecutor(2);
    }

    @AfterEach
    void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void runtimeCannotReadDirectlyAndCommitCleansTheSameConnection() throws Exception {
        configureExecutor(1);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID farmA = seed(tenantA, "A");
        UUID farmB = seed(tenantB, "B");
        TenantContext context = context(tenantA, farmA);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("select id from app.farms"))
                    .isInstanceOf(SQLException.class);
        }

        Observation result = executor.execute(context, () -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject(
                    "select count(*) from app.farms where id = ?", Integer.class, farmB
            )).isZero();
            return observe();
        });

        assertObservation(result, context, farmA);
        assertRuntimeConnectionIsClean(result.pid());
    }

    @Test
    void rollbackCleansTheSameConnectionAndUndoesPersistentData() throws Exception {
        configureExecutor(1);
        UUID tenant = UUID.randomUUID();
        UUID farm = seed(tenant, "A");
        UUID rolledBackFarm = UUID.randomUUID();
        TenantContext context = context(tenant, farm);
        int[] transactionPid = new int[1];

        IllegalStateException failure = new IllegalStateException("falha controlada");
        assertThatThrownBy(() -> executor.execute(context, () -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            transactionPid[0] = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            jdbc.update(
                    "insert into app.farms(id, tenant_id, name, status) values (?, ?, ?, 'ACTIVE')",
                    rolledBackFarm, tenant, "rollback"
            );
            throw failure;
        })).isSameAs(failure);

        assertThat(countFarmAsAdmin(rolledBackFarm)).isZero();
        assertRuntimeConnectionIsClean(transactionPid[0]);
    }

    @Test
    void nestedExecutionReusesContextAndDifferentContextFailsBeforeItsCallback() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        TenantContext contextA = context(tenantA, seed(tenantA, "A"));
        TenantContext contextB = context(tenantB, seed(tenantB, "B"));

        int[] outerPid = new int[1];
        int nestedPid = executor.execute(contextA, () -> {
            outerPid[0] = new JdbcTemplate(dataSource)
                    .queryForObject("select pg_backend_pid()", Integer.class);
            return executor.execute(contextA, () -> new JdbcTemplate(dataSource)
                    .queryForObject("select pg_backend_pid()", Integer.class));
        });
        assertThat(nestedPid).isEqualTo(outerPid[0]);

        AtomicBoolean conflictingCallbackCalled = new AtomicBoolean();
        assertThatThrownBy(() -> executor.execute(contextA, () -> executor.execute(contextB, () -> {
            conflictingCallbackCalled.set(true);
            return null;
        }))).isInstanceOf(TenantTransactionContextConflictException.class);
        assertThat(conflictingCallbackCalled).isFalse();
    }

    @Test
    void caughtNestedConflictMarksTheOuterTransactionForRollback() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        TenantContext contextA = context(tenantA, seed(tenantA, "A"));
        TenantContext contextB = context(tenantB, seed(tenantB, "B"));
        UUID farmAfterConflict = UUID.randomUUID();
        AtomicBoolean conflictingCallbackCalled = new AtomicBoolean();

        assertThatThrownBy(() -> executor.execute(contextA, () -> {
            try {
                executor.execute(contextB, () -> {
                    conflictingCallbackCalled.set(true);
                    return null;
                });
            } catch (TenantTransactionContextConflictException ignored) {
                new JdbcTemplate(dataSource).update(
                        "insert into app.farms(id, tenant_id, name, status) values (?, ?, ?, 'ACTIVE')",
                        farmAfterConflict, tenantA, "não deve persistir"
                );
            }
            return null;
        })).isInstanceOf(UnexpectedRollbackException.class);

        assertThat(conflictingCallbackCalled).isFalse();
        assertThat(countFarmAsAdmin(farmAfterConflict)).isZero();
    }

    @Test
    void partialInvalidOrRoleInconsistentContextFailsClosed() {
        UUID tenant = UUID.randomUUID();
        TenantContext context = context(tenant, UUID.randomUUID());

        assertRejectedState(context, jdbc -> jdbc.execute("set local role app_api"),
                TenantTransactionInfrastructureException.class);
        assertRejectedState(context, jdbc -> jdbc.queryForObject(
                        "select set_config('app.current_user_id', ?, true)", String.class,
                        context.userId().toString()),
                TenantTransactionInfrastructureException.class);
        assertRejectedState(context, jdbc -> jdbc.queryForObject(
                        "select set_config('app.current_tenant_id', ?, true)", String.class,
                        context.tenantId().value().toString()),
                TenantTransactionInfrastructureException.class);
        assertRejectedState(context, jdbc -> {
            jdbc.queryForObject("select set_config('app.current_user_id', ?, true)", String.class,
                    context.userId().toString());
            jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class,
                    context.tenantId().value().toString());
        }, TenantTransactionContextConflictException.class);
    }

    @Test
    void invalidSettingIsSanitizedAndPreservesTheTechnicalCause() {
        TenantContext context = context(UUID.randomUUID(), UUID.randomUUID());
        AtomicBoolean callbackCalled = new AtomicBoolean();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            new JdbcTemplate(dataSource).queryForObject(
                    "select set_config('app.current_user_id', ?, true)", String.class,
                    "valor-inválido-sensível"
            );
            executor.execute(context, () -> callbackCalled.set(true));
        }))
                .isInstanceOf(TenantTransactionInfrastructureException.class)
                .hasMessage("Não foi possível preparar o contexto transacional do tenant")
                .hasCauseInstanceOf(DataAccessException.class)
                .hasMessageNotContaining("valor-inválido-sensível");
        assertThat(callbackCalled).isFalse();
    }

    @Test
    void callbackRuntimeDatabaseAndErrorFailuresKeepTheirOriginalTypeAndIdentity() {
        TenantContext context = context(UUID.randomUUID(), UUID.randomUUID());
        IllegalStateException businessFailure = new IllegalStateException("regra de negócio");
        DataAccessException repositoryFailure = new DataRetrievalFailureException("falha do repository");
        AssertionError fatalFailure = new AssertionError("erro fatal");

        assertThatThrownBy(() -> executor.execute(context, () -> {
            throw businessFailure;
        })).isSameAs(businessFailure);
        assertThatThrownBy(() -> executor.execute(context, () -> {
            throw repositoryFailure;
        })).isSameAs(repositoryFailure);
        assertThatThrownBy(() -> executor.execute(context, () -> {
            throw fatalFailure;
        })).isSameAs(fatalFailure);
    }

    @Test
    void roleTamperingMakesNestedExecutionFailBeforeItsCallback() {
        TenantContext context = context(UUID.randomUUID(), UUID.randomUUID());
        AtomicBoolean nestedCallbackCalled = new AtomicBoolean();

        assertThatThrownBy(() -> executor.execute(context, () -> {
            new JdbcTemplate(dataSource).execute("reset role");
            return executor.execute(context, () -> {
                nestedCallbackCalled.set(true);
                return null;
            });
        })).isInstanceOf(TenantTransactionContextConflictException.class);
        assertThat(nestedCallbackCalled).isFalse();
    }

    @Test
    void concurrentCommitAndRollbackRemainIsolated() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID originalFarmA = seed(tenantA, "A");
        UUID originalFarmB = seed(tenantB, "B");
        TenantContext contextA = context(tenantA, originalFarmA);
        TenantContext contextB = context(tenantB, originalFarmB);
        UUID rolledBackFarm = UUID.randomUUID();
        UUID committedFarm = UUID.randomUUID();

        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        ConcurrentLinkedQueue<Observation> observations = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        submitConcurrentOperation(pool, done, barrier, observations, failures,
                contextA, rolledBackFarm, true);
        submitConcurrentOperation(pool, done, barrier, observations, failures,
                contextB, committedFarm, false);

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).singleElement().isInstanceOf(ControlledRollback.class);
        assertThat(observations).hasSize(2);
        assertThat(observations).anySatisfy(observation ->
                assertObservation(observation, contextA, originalFarmA, rolledBackFarm));
        assertThat(observations).anySatisfy(observation ->
                assertObservation(observation, contextB, originalFarmB, committedFarm));
        assertThat(observations.stream().map(Observation::pid).distinct()).hasSize(2);
        assertThat(countFarmAsAdmin(rolledBackFarm)).isZero();
        assertThat(countFarmAsAdmin(committedFarm)).isOne();
    }

    @Test
    void validatesArguments() {
        assertThatNullPointerException().isThrownBy(() -> executor.execute(null, () -> "x"));
        assertThatNullPointerException().isThrownBy(() -> executor.execute(
                context(UUID.randomUUID(), UUID.randomUUID()),
                (com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation<String>) null
        ));
    }

    private void configureExecutor(int maximumPoolSize) {
        if (dataSource != null) {
            dataSource.close();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(ROLE);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(0);
        dataSource = new HikariDataSource(config);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        executor = new SpringTenantTransactionExecutor(
                transactions,
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionalDatabaseRole(jdbc, new DatabaseAccessProperties("app", "app_api"))
        );
    }

    private void assertRejectedState(
            TenantContext context,
            java.util.function.Consumer<JdbcTemplate> contamination,
            Class<? extends RuntimeException> expectedType
    ) {
        AtomicBoolean callbackCalled = new AtomicBoolean();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            contamination.accept(new JdbcTemplate(dataSource));
            executor.execute(context, () -> callbackCalled.set(true));
        })).isInstanceOf(expectedType);
        assertThat(callbackCalled).isFalse();
    }

    private void submitConcurrentOperation(
            ExecutorService pool,
            CountDownLatch done,
            CyclicBarrier barrier,
            ConcurrentLinkedQueue<Observation> observations,
            ConcurrentLinkedQueue<Throwable> failures,
            TenantContext context,
            UUID insertedFarm,
            boolean rollBack
    ) {
        pool.submit(() -> {
            try {
                executor.execute(context, () -> {
                    new JdbcTemplate(dataSource).update(
                            "insert into app.farms(id, tenant_id, name, status) values (?, ?, ?, 'ACTIVE')",
                            insertedFarm, context.tenantId().value(), "concorrente"
                    );
                    observations.add(observe());
                    await(barrier);
                    if (rollBack) {
                        throw new ControlledRollback();
                    }
                });
            } catch (Throwable failure) {
                failures.add(failure);
            } finally {
                done.countDown();
            }
        });
    }

    private Observation observe() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new Observation(
                jdbc.queryForObject("select current_user", String.class),
                jdbc.queryForObject("select app.current_user_id()", UUID.class),
                jdbc.queryForObject("select app.current_tenant_id()", UUID.class),
                jdbc.queryForObject("select pg_backend_pid()", Integer.class),
                jdbc.queryForList("select id from app.farms order by id", UUID.class)
        );
    }

    private static void assertObservation(
            Observation observation,
            TenantContext context,
            UUID... expectedFarms
    ) {
        assertThat(observation.role()).isEqualTo("app_api");
        assertThat(observation.user()).isEqualTo(context.userId());
        assertThat(observation.tenant()).isEqualTo(context.tenantId().value());
        assertThat(observation.farms()).containsExactlyInAnyOrder(expectedFarms);
    }

    private void assertRuntimeConnectionIsClean(int expectedPid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select current_user,
                            current_setting('app.current_user_id', true),
                            current_setting('app.current_tenant_id', true),
                            pg_backend_pid()
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(ROLE);
            assertThat(result.getString(2)).isNullOrEmpty();
            assertThat(result.getString(3)).isNullOrEmpty();
            assertThat(result.getInt(4)).isEqualTo(expectedPid);
        }
    }

    private int countFarmAsAdmin(UUID farmId) throws SQLException {
        try (Connection connection = adminConnection();
             var statement = connection.prepareStatement("select count(*) from app.farms where id = ?")) {
            statement.setObject(1, farmId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private UUID seed(UUID tenant, String name) throws SQLException {
        UUID farm = UUID.randomUUID();
        executeAsAdmin(
                "insert into app.organizations(id, name, status) values (?, ?, 'ACTIVE')",
                tenant, "Org " + tenant
        );
        executeAsAdmin(
                "insert into app.farms(id, tenant_id, name, status) values (?, ?, ?, 'ACTIVE')",
                farm, tenant, name
        );
        return farm;
    }

    private static TenantContext context(UUID tenant, UUID farm) {
        return new TenantContext(
                new TenantId(tenant),
                UUID.randomUUID(),
                farm,
                UUID.randomUUID(),
                "OWNER",
                "ALL_FARMS"
        );
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Observation(String role, UUID user, UUID tenant, int pid, List<UUID> farms) {
    }

    private static final class ControlledRollback extends RuntimeException {
    }
}
