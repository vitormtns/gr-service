package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.herd.domain.HerdAgendaSource;
import com.gerenciadorrural.modules.herd.domain.HerdAgendaRepository.Row;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAgendaRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HerdAgendaRepositoryIntegrationTest extends PostgresMigrationTestSupport {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 1, 10);

    @Test
    void projectsExplicitFutureSanitaryDatesWithinInclusiveHorizonAndIgnoresRetractedFacts() throws Exception {
        Fixture f=fixtureWithThreeAnimals();
        seedVaccination(f,f.animals().get(0),REFERENCE.plusDays(1));
        seedVaccination(f,f.animals().get(2),REFERENCE.plusDays(16));
        executeAsAdmin("insert into app.animal_health_treatments(id,tenant_id,farm_id,animal_id,operation_id,treatment_type,occurred_on,next_due_on) values(?,?,?,?,?,'DEWORMING',?,?)",
            UUID.randomUUID(),f.tenant(),f.farm(),f.animals().get(1),UUID.randomUUID(),REFERENCE.minusDays(1),REFERENCE.plusDays(15));
        try(Connection connection=adminConnection()) {
            var r=repository(connection);
            assertThat(page(r,f,HerdAgendaSource.DERIVED,HerdPlannerType.VACCINATION,null,REFERENCE,
                REFERENCE.plusDays(15),20,0)).singleElement().satisfies(row->{
                    assertThat(row.operationalDate()).isEqualTo(REFERENCE.plusDays(1));
                    assertThat(row.pendingWorkType()).isNull();
                    assertThat(row.summary()).isEqualTo("Vacinação prevista");
                });
            assertThat(page(r,f,HerdAgendaSource.DERIVED,HerdPlannerType.DEWORMING,null,REFERENCE,
                REFERENCE.plusDays(14),20,0)).isEmpty();
            assertThat(page(r,f,HerdAgendaSource.DERIVED,HerdPlannerType.DEWORMING,null,REFERENCE,
                REFERENCE.plusDays(15),20,0)).hasSize(1);
            assertThat(r.dailyCounts(new TenantId(f.tenant()),f.farm(),REFERENCE,7,14,
                HerdAgendaSource.DERIVED,HerdPlannerType.VACCINATION,null,REFERENCE,REFERENCE.plusDays(15)))
                .singleElement().satisfies(day->assertThat(day.count()).isOne());
            assertThat(r.page(new TenantId(f.tenant()),f.farm(),REFERENCE.plusDays(1),7,14,
                HerdAgendaSource.DERIVED,HerdPlannerType.VACCINATION,f.animals().get(0),REFERENCE,
                REFERENCE.plusDays(15),20,0)).singleElement().satisfies(row->
                    assertThat(row.pendingWorkType()).hasToString("VACCINATION_DUE"));
        }
        executeAsAdmin("insert into app.animal_health_treatment_retractions(id,tenant_id,farm_id,animal_id,treatment_id,operation_id) select ?,tenant_id,farm_id,animal_id,id,? from app.animal_health_treatments where tenant_id=? and farm_id=? and animal_id=?",
            UUID.randomUUID(),UUID.randomUUID(),f.tenant(),f.farm(),f.animals().get(0));
        try(Connection connection=adminConnection()) {
            assertThat(page(repository(connection),f,HerdAgendaSource.DERIVED,HerdPlannerType.VACCINATION,
                null,REFERENCE,REFERENCE.plusDays(15),20,0)).isEmpty();
        }
    }

    @Test
    void aggregatesAllCalendarItemsBeyondFirstPageAndPreservesFiltersAndRls() throws Exception {
        Fixture f=fixtureWithThreeAnimals();
        for(int i=0;i<105;i++)seedManual(f,REFERENCE,HerdPlannerType.GENERAL,"Tarefa "+i,f.animals().getFirst());
        seedManual(f,REFERENCE.plusDays(1),HerdPlannerType.CALVING,"Conferir parto",f.animals().getFirst());
        try(Connection connection=adminConnection()) {
            var r=repository(connection);
            var days=r.dailyCounts(new TenantId(f.tenant()),f.farm(),REFERENCE,90,14,
                HerdAgendaSource.MANUAL,null,null,REFERENCE,REFERENCE.plusDays(1));
            assertThat(days).hasSize(2);
            assertThat(days.getFirst().count()).isEqualTo(105);
            assertThat(r.dailyCounts(new TenantId(f.tenant()),f.farm(),REFERENCE,90,14,
                HerdAgendaSource.MANUAL,HerdPlannerType.CALVING,null,REFERENCE,REFERENCE.plusDays(1)))
                .singleElement().satisfies(day->assertThat(day.count()).isOne());
        }
        try(Connection connection=apiConnection()) {
            setTenant(connection,UUID.randomUUID());
            assertThat(repository(connection).dailyCounts(new TenantId(f.tenant()),f.farm(),REFERENCE,90,14,
                null,null,null,REFERENCE,REFERENCE.plusDays(1))).isEmpty();
            connection.rollback();
        }
    }

    @Test
    void paginatesGloballyAcrossSixInterleavedManualAndDerivedItems() throws Exception {
        Fixture fixture = fixtureWithThreeAnimals();
        seedRecentWeight(fixture, fixture.animals().get(0), LocalDate.of(2026, 1, 9));
        seedVaccination(fixture, fixture.animals().get(0), LocalDate.of(2026, 1, 1));
        seedWeight(fixture, fixture.animals().get(1), LocalDate.of(2026, 1, 3));
        seedRecentWeight(fixture, fixture.animals().get(2), LocalDate.of(2026, 1, 9));
        seedPregnancy(fixture, fixture.animals().get(2), LocalDate.of(2026, 1, 5));
        seedManual(fixture, LocalDate.of(2026, 1, 2), HerdPlannerType.GENERAL, "Manual 2",
                fixture.animals().get(0));
        seedManual(fixture, LocalDate.of(2026, 1, 4), HerdPlannerType.GENERAL, "Manual 4",
                fixture.animals().get(1));
        seedManual(fixture, LocalDate.of(2026, 1, 6), HerdPlannerType.GENERAL, "Manual 6",
                fixture.animals().get(2));

        try (Connection connection = adminConnection()) {
            JdbcHerdAgendaRepository repository = repository(connection);
            List<Row> all = new ArrayList<>();
            for (int page = 0; page < 3; page++) {
                List<Row> rows = page(repository, fixture, null, null, null, null, null, 2, page * 2L);
                assertThat(rows).hasSize(2);
                all.addAll(rows);
            }

            assertThat(all).extracting(Row::operationalDate).containsExactly(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
                    LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4),
                    LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6));
            assertThat(all).extracting(Row::source).containsExactly(
                    HerdAgendaSource.DERIVED, HerdAgendaSource.MANUAL, HerdAgendaSource.DERIVED,
                    HerdAgendaSource.MANUAL, HerdAgendaSource.DERIVED, HerdAgendaSource.MANUAL);
            assertThat(all).extracting(Row::stableId).doesNotHaveDuplicates();
            assertThat(count(repository, fixture, null, null, null, null, null)).isEqualTo(6);
        }
    }

    @Test
    void appliesEveryFilterBeforePaginationAndPreservesManualDerivedCoexistence() throws Exception {
        Fixture fixture = fixtureWithThreeAnimals();
        UUID animal = fixture.animals().getFirst();
        seedRecentWeight(fixture, animal, LocalDate.of(2026, 1, 9));
        seedVaccination(fixture, animal, LocalDate.of(2026, 1, 5));
        seedManual(fixture, LocalDate.of(2026, 1, 5), HerdPlannerType.VACCINATION,
                "Vacinar lote", animal);
        seedRecentWeight(fixture, fixture.animals().get(1), LocalDate.of(2026, 1, 9));
        seedRecentWeight(fixture, fixture.animals().get(2), LocalDate.of(2026, 1, 9));

        long plannerItems = rowCount("app.herd_planner_items");
        long plannerOperations = rowCount("app.herd_planner_operations");
        long events = rowCount("app.animal_events");

        try (Connection connection = adminConnection()) {
            JdbcHerdAgendaRepository repository = repository(connection);
            List<Row> both = page(repository, fixture, null, HerdPlannerType.VACCINATION, animal,
                    LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 10, 0);
            assertThat(both).hasSize(2);
            assertThat(both).extracting(Row::source)
                    .containsExactly(HerdAgendaSource.DERIVED, HerdAgendaSource.MANUAL);
            assertThat(both).extracting(Row::summary)
                    .containsExactly("Vacinação pendente", "Vacinar lote");
            assertThat(page(repository, fixture, HerdAgendaSource.MANUAL, HerdPlannerType.VACCINATION, animal,
                    null, null, 10, 0)).singleElement().satisfies(row -> {
                        assertThat(row.plannerItemId()).isNotNull();
                        assertThat(row.pendingWorkType()).isNull();
                        assertThat(row.status()).hasToString("OPEN");
                    });
            assertThat(page(repository, fixture, HerdAgendaSource.DERIVED, HerdPlannerType.VACCINATION, animal,
                    null, null, 10, 0)).singleElement().satisfies(row -> {
                        assertThat(row.plannerItemId()).isNull();
                        assertThat(row.pendingWorkType()).hasToString("VACCINATION_DUE");
                        assertThat(row.identification()).isNotBlank();
                    });
        }

        assertThat(rowCount("app.herd_planner_items")).isEqualTo(plannerItems);
        assertThat(rowCount("app.herd_planner_operations")).isEqualTo(plannerOperations);
        assertThat(rowCount("app.animal_events")).isEqualTo(events);
    }

    @Test
    void usesTotalTieBreakersAndDoesNotDependOnPhysicalRowOrder() throws Exception {
        Fixture fixture = fixtureWithThreeAnimals();
        for (UUID animal : fixture.animals()) {
            seedRecentWeight(fixture, animal, LocalDate.of(2026, 1, 9));
            seedVaccination(fixture, animal, LocalDate.of(2026, 1, 7));
            seedManual(fixture, LocalDate.of(2026, 1, 7), HerdPlannerType.VACCINATION,
                    "Vacinar " + animal, animal);
        }

        try (Connection connection = adminConnection()) {
            JdbcHerdAgendaRepository repository = repository(connection);
            List<Row> first = page(repository, fixture, null, HerdPlannerType.VACCINATION,
                    null, null, null, 20, 0);
            List<Row> second = page(repository, fixture, null, HerdPlannerType.VACCINATION,
                    null, null, null, 20, 0);
            assertThat(first).hasSize(6);
            assertThat(second).extracting(Row::stableId)
                    .containsExactlyElementsOf(first.stream().map(Row::stableId).toList());
            assertThat(first).isSortedAccordingTo(Comparator.comparing(Row::operationalDate)
                    .thenComparing(row -> row.source().name())
                    .thenComparing(Row::kind)
                    .thenComparing(Row::stableId));
        }
    }

    @Test
    void manualItemsStayAtOriginWhileDerivedWorkFollowsCurrentCustody() throws Exception {
        Fixture fixture = fixtureWithThreeAnimals();
        UUID animal = fixture.animals().getFirst();
        UUID destination = UUID.randomUUID();
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                destination, fixture.tenant(), "Destino");
        seedRecentWeight(fixture, animal, LocalDate.of(2026, 1, 9));
        seedVaccination(fixture, animal, LocalDate.of(2026, 1, 5));
        UUID originPlanner = seedManual(fixture, LocalDate.of(2026, 1, 6),
                HerdPlannerType.VACCINATION, "Plano da origem", animal);

        executeAsAdmin("update app.animals set farm_id=?,version=version+1 where tenant_id=? and id=?",
                destination, fixture.tenant(), animal);

        try (Connection connection = adminConnection()) {
            JdbcHerdAgendaRepository repository = repository(connection);
            List<Row> origin = page(repository, fixture, null, null, animal, null, null, 10, 0);
            assertThat(origin).singleElement().satisfies(row -> {
                assertThat(row.source()).isEqualTo(HerdAgendaSource.MANUAL);
                assertThat(row.plannerItemId()).isEqualTo(originPlanner);
                assertThat(row.identification()).isNull();
                assertThat(row.name()).isNull();
            });

            Fixture destinationFixture = new Fixture(
                    fixture.tenant(), destination, fixture.actor(), fixture.animals());
            List<Row> atDestination = page(repository, destinationFixture, null, null, animal,
                    null, null, 10, 0);
            assertThat(atDestination).singleElement().satisfies(row -> {
                assertThat(row.source()).isEqualTo(HerdAgendaSource.DERIVED);
                assertThat(row.pendingWorkType()).hasToString("VACCINATION_DUE");
            });
        }
        assertThat(rowCount("app.herd_planner_items")).isOne();
    }

    private Fixture fixtureWithThreeAnimals() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",
                tenant, "Organização");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                farm, tenant, "Fazenda");
        List<UUID> animals = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (int index = 0; index < animals.size(); index++) {
            executeAsAdmin("""
                    insert into app.animals(id,tenant_id,farm_id,identification,name,sex,status)
                    values(?,?,?,?,?,'FEMALE','ACTIVE')
                    """, animals.get(index), tenant, farm, "A-" + index, "Animal " + index);
        }
        return new Fixture(tenant, farm, actor, animals);
    }

    private void seedRecentWeight(Fixture fixture, UUID animal, LocalDate measuredOn) throws Exception {
        seedWeight(fixture, animal, measuredOn);
    }

    private void seedWeight(Fixture fixture, UUID animal, LocalDate measuredOn) throws Exception {
        executeAsAdmin("""
                insert into app.animal_weight_measurements
                  (id,tenant_id,farm_id,animal_id,operation_id,measured_on,weight_kg,actor_user_id)
                values(?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), fixture.tenant(), fixture.farm(), animal, UUID.randomUUID(),
                measuredOn, 450, fixture.actor());
    }

    private void seedVaccination(Fixture fixture, UUID animal, LocalDate dueOn) throws Exception {
        executeAsAdmin("""
                insert into app.animal_health_treatments
                  (id,tenant_id,farm_id,animal_id,operation_id,treatment_type,occurred_on,next_due_on,actor_user_id)
                values(?,?,?,?,?,'VACCINATION',?,?,?)
                """, UUID.randomUUID(), fixture.tenant(), fixture.farm(), animal, UUID.randomUUID(),
                dueOn.minusDays(30), dueOn, fixture.actor());
    }

    private void seedPregnancy(Fixture fixture, UUID animal, LocalDate expectedOn) throws Exception {
        executeAsAdmin("""
                insert into app.animal_pregnancies
                  (id,tenant_id,farm_id,mother_animal_id,service_type,service_on,expected_calving_on,
                   operation_id,command_payload,created_by)
                values(?,?,?,?,'INSEMINATION',?,?,?,'{}',?)
                """, UUID.randomUUID(), fixture.tenant(), fixture.farm(), animal,
                expectedOn.minusDays(283), expectedOn, UUID.randomUUID(), fixture.actor());
    }

    private UUID seedManual(
            Fixture fixture,
            LocalDate scheduledFor,
            HerdPlannerType type,
            String title,
            UUID animal
    ) throws Exception {
        UUID item = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.herd_planner_items
                  (id,tenant_id,farm_id,animal_id,type,title,scheduled_for,created_by)
                values(?,?,?,?,?,?,?,?)
                """, item, fixture.tenant(), fixture.farm(), animal, type.name(), title, scheduledFor,
                fixture.actor());
        return item;
    }

    private JdbcHerdAgendaRepository repository(Connection connection) {
        return new JdbcHerdAgendaRepository(
                new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    private List<Row> page(
            JdbcHerdAgendaRepository repository,
            Fixture fixture,
            HerdAgendaSource source,
            HerdPlannerType type,
            UUID animal,
            LocalDate from,
            LocalDate to,
            int size,
            long offset
    ) {
        return repository.page(new TenantId(fixture.tenant()), fixture.farm(), REFERENCE, 7, 14,
                source, type, animal, from, to, size, offset);
    }

    private long count(
            JdbcHerdAgendaRepository repository,
            Fixture fixture,
            HerdAgendaSource source,
            HerdPlannerType type,
            UUID animal,
            LocalDate from,
            LocalDate to
    ) {
        return repository.count(new TenantId(fixture.tenant()), fixture.farm(), REFERENCE, 7, 14,
                source, type, animal, from, to);
    }

    private long rowCount(String table) throws Exception {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private record Fixture(UUID tenant, UUID farm, UUID actor, List<UUID> animals) {
    }
}
