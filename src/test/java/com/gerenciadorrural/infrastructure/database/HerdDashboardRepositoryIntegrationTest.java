package com.gerenciadorrural.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ActivityTotals;
import com.gerenciadorrural.modules.herd.domain.HealthTreatmentType;
import com.gerenciadorrural.modules.herd.domain.HerdReportRepository.LifecycleEvent;
import com.gerenciadorrural.modules.herd.domain.HerdReportRepository.TransferDirection;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdDashboardRepository;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdReportRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class HerdDashboardRepositoryIntegrationTest extends PostgresMigrationTestSupport {
  private static final LocalDate FROM = LocalDate.of(2026, 9, 7);
  private static final LocalDate TO = LocalDate.of(2026, 9, 13);

  @Test
  void derivesSnapshotActivityAttentionSeriesAndInsightsWithoutSideEffects() throws Exception {
    Fixture fixture = fixture();
    seedFacts(fixture);
    long before = factCount();

    try (Connection connection = adminConnection()) {
      JdbcHerdDashboardRepository dashboard = repository(connection);
      var snapshot = dashboard.snapshot(tenant(fixture), fixture.farm(), null, null, null);
      assertThat(snapshot.activeAnimals()).isEqualTo(2);
      assertThat(snapshot.bySex()).containsValues(1L, 1L);
      assertThat(snapshot.byCategory()).containsValue(2L);
      assertThat(snapshot.byPaddock()).singleElement().satisfies(row -> assertThat(row.total()).isOne());
      assertThat(snapshot.unlocatedAnimals()).isOne();

      ActivityTotals activity = dashboard.activity(tenant(fixture), fixture.farm(), FROM, TO);
      assertThat(activity)
          .isEqualTo(new ActivityTotals(2, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1));

      var series = dashboard.activitySeries(tenant(fixture), fixture.farm(), FROM, TO);
      assertThat(series).hasSize(7);
      assertThat(series).extracting(row -> row.date()).isSorted();
      assertThat(series.getFirst().date()).isEqualTo(FROM);
      assertThat(series.getLast().date()).isEqualTo(TO);
      assertThat(series.stream().filter(row -> row.date().equals(LocalDate.of(2026, 9, 11))).findFirst())
          .get()
          .satisfies(row -> assertThat(row.healthTreatments()).isEqualTo(2));
      assertThat(series.stream().filter(row -> row.date().equals(LocalDate.of(2026, 9, 12))).findFirst())
          .get()
          .satisfies(
              row -> {
                assertThat(row.births()).isZero();
                assertThat(row.deaths()).isZero();
                assertThat(row.sales()).isZero();
                assertThat(row.movements()).isZero();
                assertThat(row.weights()).isZero();
                assertThat(row.healthTreatments()).isZero();
                assertThat(row.breedings()).isZero();
                assertThat(row.calvings()).isZero();
              });

      var attention = dashboard.attention(tenant(fixture), fixture.farm(), TO, 90, 14);
      assertThat(attention.vaccinationDue()).isOne();
      assertThat(attention.dewormingDue()).isOne();
      assertThat(attention.weighingDue()).isOne();
      assertThat(attention.calvingUpcoming()).isOne();
      assertThat(attention.calvingOverdue()).isOne();
      assertThat(attention.plannerOpen()).isEqualTo(2);
      assertThat(attention.plannerOverdue()).isOne();

      assertThat(dashboard.reproductionPipeline(tenant(fixture), fixture.farm(), TO, 14))
          .satisfies(
              pipeline -> {
                assertThat(pipeline.openPossiblePregnancies()).isOne();
                assertThat(pipeline.openConfirmedPregnancies()).isOne();
              });

      JdbcHerdReportRepository reports = reports(connection);
      assertThat(activity.births())
          .isEqualTo(
              reports.lifecycle(
                      tenant(fixture), fixture.farm(), FROM, TO, LifecycleEvent.BORN, null, 1, 0)
                  .summary()
                  .countsByEventType()
                  .get(LifecycleEvent.BORN));
      assertThat(activity.vaccinations())
          .isEqualTo(
              reports.health(tenant(fixture), fixture.farm(), FROM, TO, null, null, 1, 0)
                  .summary()
                  .countsByTreatmentType()
                  .get(HealthTreatmentType.VACCINATION));
      assertThat(activity.calvings())
          .isEqualTo(
              reports.reproduction(tenant(fixture), fixture.farm(), FROM, TO, null, null, null, 1, 0)
                  .summary()
                  .calvings());
      assertThat(activity.plannerCompleted())
          .isEqualTo(
              reports.planner(tenant(fixture), fixture.farm(), FROM, TO, TO, null, null, null, 1, 0)
                  .summary()
                  .completed());
    }

    assertThat(factCount()).isEqualTo(before);
  }

  @Test
  void preservesOriginHistoryCurrentCustodyAndTenantRls() throws Exception {
    Fixture fixture = fixture();
    seedFacts(fixture);

    try (Connection connection = adminConnection()) {
      JdbcHerdDashboardRepository dashboard = repository(connection);
      assertThat(dashboard.snapshot(tenant(fixture), fixture.farm(), null, null, null).activeAnimals())
          .isEqualTo(2);
      assertThat(dashboard.snapshot(tenant(fixture), fixture.otherFarm(), null, null, null).activeAnimals())
          .isOne();
      assertThat(dashboard.activity(tenant(fixture), fixture.farm(), FROM, TO).transfersOut())
          .isOne();
      assertThat(dashboard.activity(tenant(fixture), fixture.otherFarm(), FROM, TO).transfersIn())
          .isOne();
    }

    UUID outsiderTenant = UUID.randomUUID();
    UUID outsiderFarm = UUID.randomUUID();
    organization(outsiderTenant, outsiderFarm);
    try (Connection connection = apiConnection()) {
      setTenant(connection, outsiderTenant);
      JdbcHerdDashboardRepository dashboard = repository(connection);
      assertThat(dashboard.snapshot(tenant(fixture), fixture.farm(), null, null, null).activeAnimals())
          .isZero();
      assertThat(dashboard.activity(tenant(fixture), fixture.farm(), FROM, TO))
          .isEqualTo(new ActivityTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
      assertThat(dashboard.attention(tenant(fixture), fixture.farm(), TO, 90, 14))
          .satisfies(
              value -> {
                assertThat(value.vaccinationDue()).isZero();
                assertThat(value.plannerOpen()).isZero();
              });
      connection.rollback();
    }
  }

  @Test
  void returnsCoherentEmptyModelsForAValidEmptyFarm() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    organization(tenant, farm);
    try (Connection connection = adminConnection()) {
      JdbcHerdDashboardRepository dashboard = repository(connection);
      assertThat(dashboard.snapshot(new TenantId(tenant), farm, null, null, null).activeAnimals())
          .isZero();
      assertThat(dashboard.activitySeries(new TenantId(tenant), farm, FROM, TO)).hasSize(7);
      assertThat(dashboard.activitySeries(new TenantId(tenant), farm, FROM, TO))
          .allSatisfy(
              row -> {
                assertThat(row.births()).isZero();
                assertThat(row.healthTreatments()).isZero();
              });
      assertThat(dashboard.reproductionPipeline(new TenantId(tenant), farm, TO, 14))
          .satisfies(
              value -> {
                assertThat(value.openPossiblePregnancies()).isZero();
                assertThat(value.openConfirmedPregnancies()).isZero();
              });
    }
  }

  private Fixture fixture() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    UUID otherFarm = UUID.randomUUID();
    UUID paddock = UUID.randomUUID();
    organization(tenant, farm);
    executeAsAdmin(
        "insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
        otherFarm,
        tenant,
        "Fazenda Destino");
    executeAsAdmin(
        "insert into app.paddocks(id,tenant_id,farm_id,name,status) values(?,?,?,?,'ACTIVE')",
        paddock,
        tenant,
        farm,
        "Pasto Norte");
    UUID female = animal(tenant, farm, "A-1", "FEMALE", "ACTIVE", paddock);
    UUID male = animal(tenant, farm, "A-2", "MALE", "ACTIVE", null);
    UUID sold = animal(tenant, farm, "A-3", "MALE", "SOLD", null);
    UUID deceased = animal(tenant, farm, "A-4", "FEMALE", "DECEASED", null);
    UUID transferred = animal(tenant, farm, "A-5", "FEMALE", "ACTIVE", null);
    return new Fixture(tenant, farm, otherFarm, paddock, female, male, sold, deceased, transferred);
  }

  private void seedFacts(Fixture f) throws Exception {
    event(f, f.male(), "CREATED", FROM, null, "{}");
    event(f, f.male(), "BORN", FROM, UUID.randomUUID(), "{}");
    event(f, f.sold(), "SOLD", TO, UUID.randomUUID(), "{}");
    event(f, f.deceased(), "DECEASED", TO, UUID.randomUUID(), "{}");
    UUID movement = UUID.randomUUID();
    event(f, f.female(), "MOVED", LocalDate.of(2026, 9, 8), movement, "{}");
    UUID movementEvent = lastEvent(movement);
    executeAsAdmin(
        "insert into app.herd_movements(event_id,tenant_id,farm_id,animal_id,operation_id,destination_paddock_id) values(?,?,?,?,?,?)",
        movementEvent,
        f.tenant(),
        f.farm(),
        f.female(),
        UUID.randomUUID(),
        f.paddock());
    executeAsAdmin(
        "insert into app.animal_transfers(id,tenant_id,animal_id,operation_id,source_farm_id,destination_farm_id,source_farm_name,destination_farm_name,occurred_on,original_expected_version,resulting_version) values(?,?,?,?,?,?,?,?,?,0,1)",
        UUID.randomUUID(),
        f.tenant(),
        f.transferred(),
        UUID.randomUUID(),
        f.farm(),
        f.otherFarm(),
        "Fazenda Origem",
        "Fazenda Destino",
        LocalDate.of(2026, 9, 9));
    executeAsAdmin(
        "update app.animals set farm_id=? where tenant_id=? and id=?",
        f.otherFarm(),
        f.tenant(),
        f.transferred());
    executeAsAdmin(
        "insert into app.animal_weight_measurements(id,tenant_id,farm_id,animal_id,operation_id,measured_on,weight_kg) values(?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        f.male(),
        UUID.randomUUID(),
        LocalDate.of(2026, 9, 10),
        400);
    health(f, "VACCINATION");
    health(f, "DEWORMING");
    event(f, f.female(), "BREEDING_RECORDED", LocalDate.of(2026, 9, 10), UUID.randomUUID(), "{}");
    event(f, f.female(), "PREGNANCY_CONFIRMED", LocalDate.of(2026, 9, 10), UUID.randomUUID(), "{}");
    event(f, f.female(), "PREGNANCY_ENDED", LocalDate.of(2026, 9, 10), UUID.randomUUID(), "{}");
    event(f, f.female(), "CALVED", LocalDate.of(2026, 9, 10), UUID.randomUUID(), "{}");
    event(f, f.female(), "BORN", LocalDate.of(2026, 9, 10), UUID.randomUUID(), "{}");
    pregnancy(f, f.female(), "POSSIBLE", LocalDate.of(2026, 9, 20));
    pregnancy(f, f.male(), "CONFIRMED", LocalDate.of(2026, 9, 1));
    planner(f, "OPEN", LocalDate.of(2026, 9, 1));
    planner(f, "OPEN", LocalDate.of(2026, 9, 20));
    planner(f, "COMPLETED", LocalDate.of(2026, 9, 12));
    planner(f, "CANCELLED", LocalDate.of(2026, 9, 13));
  }

  private static void organization(UUID tenant, UUID farm) throws Exception {
    executeAsAdmin(
        "insert into app.organizations(id,name,status) values(?,?,'ACTIVE')", tenant, "Organização");
    executeAsAdmin(
        "insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
        farm,
        tenant,
        "Fazenda");
  }

  private static UUID animal(
      UUID tenant, UUID farm, String identification, String sex, String status, UUID paddock)
      throws Exception {
    UUID id = UUID.randomUUID();
    executeAsAdmin(
        "insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status,paddock_id) values(?,?,?,?,?,'2024-01-01',?,?)",
        id,
        tenant,
        farm,
        identification,
        sex,
        status,
        paddock);
    return id;
  }

  private static void event(
      Fixture f, UUID animal, String type, LocalDate day, UUID operation, String payload)
      throws Exception {
    executeAsAdmin(
        "insert into app.animal_events(id,tenant_id,farm_id,animal_id,event_type,operation_id,occurred_on,resulting_version,payload) values(?,?,?,?,?,?,?,?,cast(? as jsonb))",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        animal,
        type,
        operation,
        day,
        0,
        payload);
  }

  private static UUID lastEvent(UUID operation) throws Exception {
    try (Connection connection = adminConnection();
        var statement = connection.prepareStatement("select id from app.animal_events where operation_id=?")) {
      statement.setObject(1, operation);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getObject(1, UUID.class);
      }
    }
  }

  private static void health(Fixture f, String type) throws Exception {
    UUID animal = type.equals("VACCINATION") ? f.female() : f.male();
    executeAsAdmin(
        "insert into app.animal_health_treatments(id,tenant_id,farm_id,animal_id,operation_id,treatment_type,occurred_on,next_due_on) values(?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        animal,
        UUID.randomUUID(),
        type,
        LocalDate.of(2026, 9, 11),
        LocalDate.of(2026, 9, 12));
  }

  private static void pregnancy(Fixture f, UUID mother, String status, LocalDate due)
      throws Exception {
    executeAsAdmin(
        "insert into app.animal_pregnancies(id,tenant_id,farm_id,mother_animal_id,service_type,service_on,expected_calving_on,status,confirmed_on,operation_id,command_payload) values(?,?,?,?,'INSEMINATION','2025-12-01',?,?,case when ?='CONFIRMED' then date '2026-01-01' else null end,?,'{}')",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        mother,
        due,
        status,
        status,
        UUID.randomUUID());
  }

  private static void planner(Fixture f, String status, LocalDate scheduled) throws Exception {
    executeAsAdmin(
        "insert into app.herd_planner_items(id,tenant_id,farm_id,type,title,scheduled_for,status,created_by,completed_at,cancelled_at) values(?,?,?,'GENERAL','Manejo',?,?,?,case when ?='COMPLETED' then now() else null end,case when ?='CANCELLED' then now() else null end)",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        scheduled,
        status,
        UUID.randomUUID(),
        status,
        status);
  }

  private static JdbcHerdDashboardRepository repository(Connection connection) {
    return new JdbcHerdDashboardRepository(
        new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
  }

  private static JdbcHerdReportRepository reports(Connection connection) {
    return new JdbcHerdReportRepository(
        new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
  }

  private static TenantId tenant(Fixture fixture) {
    return new TenantId(fixture.tenant());
  }

  private static long factCount() throws Exception {
    try (Connection connection = adminConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "select (select count(*) from app.animals)+(select count(*) from app.animal_events)+(select count(*) from app.herd_movements)+(select count(*) from app.animal_transfers)+(select count(*) from app.animal_weight_measurements)+(select count(*) from app.animal_health_treatments)+(select count(*) from app.animal_pregnancies)+(select count(*) from app.herd_planner_items)")) {
      result.next();
      return result.getLong(1);
    }
  }

  private record Fixture(
      UUID tenant,
      UUID farm,
      UUID otherFarm,
      UUID paddock,
      UUID female,
      UUID male,
      UUID sold,
      UUID deceased,
      UUID transferred) {}
}
