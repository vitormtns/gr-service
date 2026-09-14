package com.gerenciadorrural.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.HerdAnimalCommandInvalidException;
import com.gerenciadorrural.modules.herd.application.HerdPlannerExceptions;
import com.gerenciadorrural.modules.herd.application.HerdPlannerService;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerStatus;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalProfileRepository;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdPlannerOperationRepository;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdPlannerRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
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

class HerdPlannerServiceIntegrationTest extends PostgresMigrationTestSupport {

    private HikariDataSource dataSource;
    private HerdPlannerService service;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    do $$ begin
                      if not exists(select 1 from pg_roles where rolname='planner_runtime') then
                        create role planner_runtime login noinherit nosuperuser password 'planner-test';
                      end if;
                      grant app_api to planner_runtime;
                    end $$
                    """);
        }
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(POSTGRES.getJdbcUrl());
        configuration.setUsername("planner_runtime");
        configuration.setPassword("planner-test");
        configuration.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(configuration);
        var namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        var transactions = new SpringTenantTransactionExecutor(
                new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource)),
                namedJdbc,
                new TransactionalDatabaseRole(new JdbcTemplate(dataSource),
                        new DatabaseAccessProperties("app", "app_api"))
        );
        service = new HerdPlannerService(
                transactions,
                new JdbcHerdPlannerRepository(namedJdbc),
                new JdbcHerdPlannerOperationRepository(namedJdbc),
                new JdbcHerdAnimalProfileRepository(namedJdbc),
                new ObjectMapper()
        );
    }

    @AfterEach
    void close() throws Exception {
        dropFailureTrigger();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void preservesHistoricalReplaysAndRejectsSemanticOperationConflicts() throws Exception {
        Fixture fixture = fixture();
        UUID createOperation = UUID.randomUUID();
        UUID correctOperation = UUID.randomUUID();
        UUID completeOperation = UUID.randomUUID();

        var created = service.create(fixture.context(), command(
                createOperation, null, HerdPlannerType.GENERAL, " Inspecionar ", " Curral norte ",
                LocalDate.of(2026, 1, 2), null));
        var corrected = service.correct(fixture.context(), created.item().id(), command(
                correctOperation, 0L, HerdPlannerType.WEIGHING, " Pesar ", " Balança principal ",
                LocalDate.of(2026, 1, 3), null));
        var completed = service.transition(
                fixture.context(), created.item().id(), completeOperation, 1, HerdPlannerStatus.COMPLETED);

        assertThat(created.item().title()).isEqualTo("Inspecionar");
        assertThat(corrected.item().version()).isOne();
        assertThat(completed.item().version()).isEqualTo(2);
        assertThat(service.create(fixture.context(), command(
                createOperation, null, HerdPlannerType.GENERAL, "Inspecionar", "Curral norte",
                LocalDate.of(2026, 1, 2), null)).replay()).isTrue();
        assertThat(service.correct(fixture.context(), created.item().id(), command(
                correctOperation, 0L, HerdPlannerType.WEIGHING, "Pesar", "Balança principal",
                LocalDate.of(2026, 1, 3), null)).replay()).isTrue();
        assertThat(service.transition(
                fixture.context(), created.item().id(), completeOperation, 1, HerdPlannerStatus.COMPLETED).replay())
                .isTrue();
        assertThat(count("app.herd_planner_operations", fixture.tenant())).isEqualTo(3);

        assertThatThrownBy(() -> service.create(fixture.context(), command(
                createOperation, null, HerdPlannerType.GENERAL, "Outro título", "Curral norte",
                LocalDate.of(2026, 1, 2), null))).isInstanceOf(HerdPlannerExceptions.Conflict.class);
        assertThatThrownBy(() -> service.transition(
                fixture.context(), created.item().id(), createOperation, 2, HerdPlannerStatus.CANCELLED))
                .isInstanceOf(HerdPlannerExceptions.Conflict.class);
    }

    @Test
    void optimisticLockingSeparatesReplaysFromNewStaleCommands() throws Exception {
        Fixture fixture = fixture();
        var item = service.create(fixture.context(), command(
                UUID.randomUUID(), null, HerdPlannerType.GENERAL, "Inspecionar", null,
                LocalDate.of(2025, 1, 1), null)).item();
        UUID correctionOperation = UUID.randomUUID();
        var corrected = service.correct(fixture.context(), item.id(), command(
                correctionOperation, 0L, HerdPlannerType.MOVEMENT, "Mover lote", null,
                LocalDate.of(2025, 1, 2), null));

        assertThat(corrected.item().version()).isOne();
        assertThatThrownBy(() -> service.correct(fixture.context(), item.id(), command(
                UUID.randomUUID(), 0L, HerdPlannerType.CALVING, "Conferir parto", null,
                LocalDate.of(2025, 1, 3), null))).isInstanceOf(HerdPlannerExceptions.Conflict.class);
        assertThatThrownBy(() -> service.transition(
                fixture.context(), item.id(), UUID.randomUUID(), 0, HerdPlannerStatus.COMPLETED))
                .isInstanceOf(HerdPlannerExceptions.Conflict.class);
        assertThatThrownBy(() -> service.transition(
                fixture.context(), item.id(), UUID.randomUUID(), 0, HerdPlannerStatus.CANCELLED))
                .isInstanceOf(HerdPlannerExceptions.Conflict.class);
        assertThat(service.correct(fixture.context(), item.id(), command(
                correctionOperation, 0L, HerdPlannerType.MOVEMENT, "Mover lote", null,
                LocalDate.of(2025, 1, 2), null)).replay()).isTrue();
        assertThatThrownBy(() -> service.create(fixture.context(), command(
                UUID.randomUUID(), 0L, HerdPlannerType.GENERAL, "Inválido", null,
                LocalDate.now(), null))).isInstanceOf(HerdAnimalCommandInvalidException.class);
    }

    @Test
    void concurrentCorrectionsHaveOneWholeWinnerAndOneConflict() throws Exception {
        Fixture fixture = fixture();
        var item = service.create(fixture.context(), command(
                UUID.randomUUID(), null, HerdPlannerType.GENERAL, "Original", "Antes",
                LocalDate.of(2026, 1, 1), null)).item();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> run(barrier, () -> service.correct(
                    fixture.context(), item.id(), command(UUID.randomUUID(), 0L, HerdPlannerType.WEIGHING,
                            "Vencedor A", "Notas A", LocalDate.of(2026, 2, 1), null))));
            Future<Object> second = pool.submit(() -> run(barrier, () -> service.correct(
                    fixture.context(), item.id(), command(UUID.randomUUID(), 0L, HerdPlannerType.VACCINATION,
                            "Vencedor B", "Notas B", LocalDate.of(2026, 3, 1), null))));
            List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(HerdPlannerService.Mutation.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(HerdPlannerServiceIntegrationTest::isConflict)).hasSize(1);
            var stored = service.detail(fixture.context(), item.id());
            assertThat(stored.version()).isOne();
            if (stored.type() == HerdPlannerType.WEIGHING) {
                assertThat(stored.title()).isEqualTo("Vencedor A");
                assertThat(stored.notes()).isEqualTo("Notas A");
                assertThat(stored.scheduledFor()).isEqualTo(LocalDate.of(2026, 2, 1));
            } else {
                assertThat(stored.type()).isEqualTo(HerdPlannerType.VACCINATION);
                assertThat(stored.title()).isEqualTo("Vencedor B");
                assertThat(stored.notes()).isEqualTo("Notas B");
                assertThat(stored.scheduledFor()).isEqualTo(LocalDate.of(2026, 3, 1));
            }
            assertThat(count("app.herd_planner_operations", fixture.tenant())).isEqualTo(2);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentCompletionAndCancellationHaveExactlyOneWinner() throws Exception {
        Fixture fixture = fixture();
        var item = service.create(fixture.context(), command(
                UUID.randomUUID(), null, HerdPlannerType.GENERAL, "Inspecionar", null,
                LocalDate.now(), null)).item();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> complete = pool.submit(() -> run(barrier, () -> service.transition(
                    fixture.context(), item.id(), UUID.randomUUID(), 0, HerdPlannerStatus.COMPLETED)));
            Future<Object> cancel = pool.submit(() -> run(barrier, () -> service.transition(
                    fixture.context(), item.id(), UUID.randomUUID(), 0, HerdPlannerStatus.CANCELLED)));
            List<Object> outcomes = List.of(complete.get(10, TimeUnit.SECONDS), cancel.get(10, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(HerdPlannerService.Mutation.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(HerdPlannerServiceIntegrationTest::isConflict)).hasSize(1);
            assertThat(service.detail(fixture.context(), item.id()).status())
                    .isIn(HerdPlannerStatus.COMPLETED, HerdPlannerStatus.CANCELLED);
            assertThat(count("app.herd_planner_operations", fixture.tenant())).isEqualTo(2);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentSameCompletionOperationReplaysWithoutDoubleVersion() throws Exception {
        Fixture fixture = fixture();
        var item = service.create(fixture.context(), command(
                UUID.randomUUID(), null, HerdPlannerType.GENERAL, "Inspecionar", null,
                LocalDate.now(), null)).item();
        UUID operation = UUID.randomUUID();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> run(barrier, () -> service.transition(
                    fixture.context(), item.id(), operation, 0, HerdPlannerStatus.COMPLETED)));
            Future<Object> second = pool.submit(() -> run(barrier, () -> service.transition(
                    fixture.context(), item.id(), operation, 0, HerdPlannerStatus.COMPLETED)));
            List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).allMatch(HerdPlannerService.Mutation.class::isInstance);
            assertThat(outcomes.stream().map(HerdPlannerService.Mutation.class::cast)
                    .filter(HerdPlannerService.Mutation::replay)).hasSize(1);
            assertThat(service.detail(fixture.context(), item.id()).version()).isOne();
            assertThat(count("app.herd_planner_operations", fixture.tenant())).isEqualTo(2);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rollsBackCreateCorrectAndCompleteAfterPartialWritesAndAllowsRetry() throws Exception {
        Fixture fixture = fixture();

        UUID createOperation = UUID.randomUUID();
        installFailureTrigger();
        assertThatThrownBy(() -> service.create(fixture.context(), command(
                createOperation, null, HerdPlannerType.GENERAL, "Criar", null,
                LocalDate.of(2026, 4, 1), null))).isInstanceOf(RuntimeException.class);
        dropFailureTrigger();
        assertThat(count("app.herd_planner_items", fixture.tenant())).isZero();
        assertThat(count("app.herd_planner_operations", fixture.tenant())).isZero();
        var item = service.create(fixture.context(), command(
                createOperation, null, HerdPlannerType.GENERAL, "Criar", null,
                LocalDate.of(2026, 4, 1), null)).item();

        UUID correctionOperation = UUID.randomUUID();
        installFailureTrigger();
        assertThatThrownBy(() -> service.correct(fixture.context(), item.id(), command(
                correctionOperation, 0L, HerdPlannerType.WEIGHING, "Corrigir", "Depois",
                LocalDate.of(2026, 4, 2), null))).isInstanceOf(RuntimeException.class);
        dropFailureTrigger();
        var afterCorrectionFailure = service.detail(fixture.context(), item.id());
        assertThat(afterCorrectionFailure.title()).isEqualTo("Criar");
        assertThat(afterCorrectionFailure.version()).isZero();
        assertThat(count("app.herd_planner_operations", fixture.tenant())).isOne();
        var corrected = service.correct(fixture.context(), item.id(), command(
                correctionOperation, 0L, HerdPlannerType.WEIGHING, "Corrigir", "Depois",
                LocalDate.of(2026, 4, 2), null)).item();

        UUID completionOperation = UUID.randomUUID();
        installFailureTrigger();
        assertThatThrownBy(() -> service.transition(
                fixture.context(), item.id(), completionOperation, 1, HerdPlannerStatus.COMPLETED))
                .isInstanceOf(RuntimeException.class);
        dropFailureTrigger();
        var afterCompletionFailure = service.detail(fixture.context(), item.id());
        assertThat(afterCompletionFailure.status()).isEqualTo(HerdPlannerStatus.OPEN);
        assertThat(afterCompletionFailure.version()).isEqualTo(corrected.version());
        assertThat(afterCompletionFailure.completedAt()).isNull();
        assertThat(afterCompletionFailure.cancelledAt()).isNull();
        assertThat(count("app.herd_planner_operations", fixture.tenant())).isEqualTo(2);
        assertThat(service.transition(
                fixture.context(), item.id(), completionOperation, 1, HerdPlannerStatus.COMPLETED).item().status())
                .isEqualTo(HerdPlannerStatus.COMPLETED);
    }

    @Test
    void completingPlannerItemsCreatesNoDomainFacts() throws Exception {
        Fixture fixture = fixture();
        for (HerdPlannerType type : List.of(
                HerdPlannerType.WEIGHING, HerdPlannerType.VACCINATION, HerdPlannerType.CALVING)) {
            var item = service.create(fixture.context(), command(
                    UUID.randomUUID(), null, type, "Planejar " + type, null, LocalDate.now(), null)).item();
            service.transition(fixture.context(), item.id(), UUID.randomUUID(), 0, HerdPlannerStatus.COMPLETED);
        }

        assertThat(rowCount("app.animal_weight_measurements")).isZero();
        assertThat(rowCount("app.animal_health_treatments")).isZero();
        assertThat(rowCount("app.animal_pregnancies")).isZero();
        assertThat(rowCount("app.animal_maternal_relations")).isZero();
        assertThat(rowCount("app.animal_events")).isZero();
        assertThat(rowCount("app.animals")).isZero();
        assertThat(count("app.herd_planner_items", fixture.tenant())).isEqualTo(3);
        assertThat(count("app.herd_planner_operations", fixture.tenant())).isEqualTo(6);
    }

    @Test
    void listFiltersAndPaginatesDeterministicallyWithinTenantAndFarm() throws Exception {
        Fixture fixture = fixture();
        UUID firstAnimal = UUID.randomUUID();
        UUID secondAnimal = UUID.randomUUID();
        for (UUID animal : List.of(firstAnimal, secondAnimal)) {
            executeAsAdmin("""
                    insert into app.animals(id,tenant_id,farm_id,identification,sex,status)
                    values(?,?,?,?,?,'ACTIVE')
                    """, animal, fixture.tenant(), fixture.farm(), "A-" + animal, "FEMALE");
        }
        var first = service.create(fixture.context(), command(UUID.randomUUID(), null,
                HerdPlannerType.WEIGHING, "Primeiro", null, LocalDate.of(2026, 5, 1), firstAnimal)).item();
        var second = service.create(fixture.context(), command(UUID.randomUUID(), null,
                HerdPlannerType.VACCINATION, "Segundo", null, LocalDate.of(2026, 5, 2), secondAnimal)).item();
        var third = service.create(fixture.context(), command(UUID.randomUUID(), null,
                HerdPlannerType.WEIGHING, "Terceiro", null, LocalDate.of(2026, 5, 3), firstAnimal)).item();
        service.transition(fixture.context(), third.id(), UUID.randomUUID(), 0, HerdPlannerStatus.CANCELLED);

        var pageZero = service.page(fixture.context(), null, null, null, null, null, 0, 2);
        var pageOne = service.page(fixture.context(), null, null, null, null, null, 1, 2);
        assertThat(pageZero.totalElements()).isEqualTo(3);
        assertThat(pageZero.items()).extracting(item -> item.id()).containsExactly(first.id(), second.id());
        assertThat(pageOne.items()).extracting(item -> item.id()).containsExactly(third.id());
        assertThat(service.page(fixture.context(), HerdPlannerStatus.OPEN, null, null,
                null, null, 0, 10).items()).hasSize(2);
        assertThat(service.page(fixture.context(), null, HerdPlannerType.WEIGHING, null,
                null, null, 0, 10).items()).hasSize(2);
        assertThat(service.page(fixture.context(), null, null, firstAnimal,
                null, null, 0, 10).items()).hasSize(2);
        assertThat(service.page(fixture.context(), null, null, null,
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 2), 0, 10).items())
                .extracting(item -> item.id()).containsExactly(second.id());

        UUID otherFarm = UUID.randomUUID();
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                otherFarm, fixture.tenant(), "Outra fazenda");
        TenantContext otherFarmContext = new TenantContext(
                new TenantId(fixture.tenant()), UUID.randomUUID(), otherFarm, UUID.randomUUID(),
                "OWNER", "ALL_FARMS");
        assertThat(service.page(otherFarmContext, null, null, null, null, null, 0, 10).items()).isEmpty();
    }

    private Fixture fixture() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')", tenant, "Organização");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                farm, tenant, "Fazenda");
        return new Fixture(tenant, farm, new TenantContext(
                new TenantId(tenant), UUID.randomUUID(), farm, UUID.randomUUID(), "OWNER", "ALL_FARMS"));
    }

    private static HerdPlannerService.Command command(
            UUID operationId,
            Long expectedVersion,
            HerdPlannerType type,
            String title,
            String notes,
            LocalDate scheduledFor,
            UUID animalId
    ) {
        return new HerdPlannerService.Command(
                operationId, expectedVersion, type, title, notes, scheduledFor, animalId);
    }

    private static Object run(CyclicBarrier barrier, Callable<HerdPlannerService.Mutation> task) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            return task.call();
        } catch (HerdPlannerExceptions.Conflict | HerdPlannerExceptions.InvalidState conflict) {
            return conflict;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean isConflict(Object value) {
        return value instanceof HerdPlannerExceptions.Conflict
                || value instanceof HerdPlannerExceptions.InvalidState;
    }

    private void installFailureTrigger() throws Exception {
        executeAsAdmin("""
                create or replace function app.fail_planner_operation_insert() returns trigger
                language plpgsql as $$ begin raise exception 'planner fault injection'; end $$;
                create trigger fail_planner_operation_insert
                before insert on app.herd_planner_operations
                for each row execute function app.fail_planner_operation_insert()
                """);
    }

    private void dropFailureTrigger() throws Exception {
        executeAsAdmin("""
                drop trigger if exists fail_planner_operation_insert on app.herd_planner_operations;
                drop function if exists app.fail_planner_operation_insert()
                """);
    }

    private long count(String table, UUID tenant) throws Exception {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from " + table + " where tenant_id=?")) {
            statement.setObject(1, tenant);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private long rowCount(String table) throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private record Fixture(UUID tenant, UUID farm, TenantContext context) {
    }
}
