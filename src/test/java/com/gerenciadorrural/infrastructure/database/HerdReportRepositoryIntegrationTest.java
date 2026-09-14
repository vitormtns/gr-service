package com.gerenciadorrural.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.domain.HerdReportRepository.*;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdReportRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class HerdReportRepositoryIntegrationTest extends PostgresMigrationTestSupport {
  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 12, 31);

  @Test
  void derivesAllExplicitReportsWithoutChangingOperationalFacts() throws Exception {
    Fixture f = fixture();
    seedFacts(f);
    long factsBefore = factCount();

    try (Connection connection = adminConnection()) {
      JdbcHerdReportRepository reports = repository(connection);

      var position =
          reports.herdPosition(
              tenant(f), f.farm(), null, null, null, 20, 0);
      assertThat(position.summary().totalActiveAnimals()).isEqualTo(2);
      assertThat(position.summary().totalsByCategory())
          .containsEntry(HerdReportCategory.UNCLASSIFIED, 2L);
      assertThat(position.summary().totalsBySex())
          .containsEntry(HerdAnimalSex.FEMALE, 1L)
          .containsEntry(HerdAnimalSex.MALE, 1L);
      assertThat(position.summary().unlocatedCount()).isOne();
      assertThat(position.items()).hasSize(2);

      var lifecycle =
          reports.lifecycle(tenant(f), f.farm(), FROM, TO, null, null, 20, 0);
      assertThat(lifecycle.summary().countsByEventType())
          .containsEntry(LifecycleEvent.CREATED, 4L)
          .containsEntry(LifecycleEvent.BORN, 1L)
          .containsEntry(LifecycleEvent.SOLD, 1L)
          .containsEntry(LifecycleEvent.DECEASED, 1L);
      assertThat(lifecycle.summary().totalAffectedAnimals()).isEqualTo(4);
      assertThat(lifecycle.totalElements()).isEqualTo(7);

      var movements =
          reports.movements(
              tenant(f), f.farm(), FROM, TO, f.activeFemale(), f.sourcePaddock(), null, 20, 0);
      assertThat(movements.summary()).isEqualTo(new MovementSummary(1, 1));
      assertThat(movements.items().getFirst().sourcePaddock().name()).isEqualTo("Pasto Sul");
      assertThat(movements.items().getFirst().destinationPaddock().name())
          .isEqualTo("Pasto Norte");

      var transfers =
          reports.transfers(
              tenant(f), f.farm(), FROM, TO, TransferDirection.ALL, null, 20, 0);
      assertThat(transfers.summary()).isEqualTo(new TransferSummary(1, 0, 1, 1));
      assertThat(transfers.items().getFirst()).satisfies(
          item -> {
            assertThat(item.direction()).isEqualTo(TransferDirection.OUT);
            assertThat(item.sourceFarm()).isNotNull();
            assertThat(item.destinationFarm()).isNull();
            assertThat(item.destinationPaddock()).isNull();
          });

      var weights = reports.weights(tenant(f), f.farm(), FROM, TO, null, null, 20, 0);
      assertThat(weights.summary().measurementCount()).isEqualTo(2);
      assertThat(weights.summary().animalsMeasured()).isOne();
      assertThat(weights.summary().averageWeight()).isEqualByComparingTo("425.0000000000000000");
      assertThat(weights.summary().minimumWeight()).isEqualByComparingTo("400.000");
      assertThat(weights.summary().maximumWeight()).isEqualByComparingTo("450.000");
      assertThat(weights.items()).extracting(WeightItem::weight)
          .containsExactly(new BigDecimal("450.000"), new BigDecimal("400.000"));

      var health = reports.health(tenant(f), f.farm(), FROM, TO, null, null, 20, 0);
      assertThat(health.summary().treatmentsCount()).isEqualTo(2);
      assertThat(health.summary().animalsTreated()).isOne();
      assertThat(health.summary().countsByTreatmentType())
          .containsEntry(HealthTreatmentType.VACCINATION, 1L)
          .containsEntry(HealthTreatmentType.DEWORMING, 1L);
      assertThat(health.items().getFirst().product()).isNotBlank();

      var reproduction =
          reports.reproduction(tenant(f), f.farm(), FROM, TO, null, null, null, 20, 0);
      assertThat(reproduction.summary())
          .isEqualTo(new ReproductionSummary(1, 1, 1, 1, 1, 1, 0));
      assertThat(reproduction.items()).extracting(ReproductionItem::action)
          .containsExactlyInAnyOrder(
              ReproductionAction.BREEDING_RECORDED,
              ReproductionAction.PREGNANCY_CONFIRMED,
              ReproductionAction.PREGNANCY_TERMINATED,
              ReproductionAction.CALVED,
              ReproductionAction.BORN);

      var planner =
          reports.planner(
              tenant(f),
              f.farm(),
              FROM,
              TO,
              LocalDate.of(2026, 6, 1),
              null,
              null,
              null,
              20,
              0);
      assertThat(planner.summary()).isEqualTo(new PlannerSummary(2, 1, 1, 1));
      assertThat(planner.items()).hasSize(4);
    }

    assertThat(factCount()).isEqualTo(factsBefore);
  }

  @Test
  void keepsHistoricalRowsAtOriginAndRlsRejectsAnotherTenant() throws Exception {
    Fixture f = fixture();
    seedFacts(f);
    executeAsAdmin(
        "update app.animals set farm_id=?,paddock_id=null where tenant_id=? and id=?",
        f.otherFarm(),
        f.tenant(),
        f.activeFemale());

    try (Connection connection = adminConnection()) {
      JdbcHerdReportRepository reports = repository(connection);
      assertThat(reports.herdPosition(tenant(f), f.farm(), null, null, null, 20, 0).items())
          .extracting(item -> item.animal().id())
          .doesNotContain(f.activeFemale());
      assertThat(reports.weights(tenant(f), f.farm(), FROM, TO, f.activeFemale(), null, 20, 0).items())
          .hasSize(2)
          .allSatisfy(item -> assertThat(item.animal().identification()).isEqualTo("A-1"));
      assertThat(reports.movements(tenant(f), f.farm(), FROM, TO, f.activeFemale(), null, null, 20, 0)
              .items())
          .hasSize(1);
    }

    Fixture outsider = fixture();
    try (Connection connection = apiConnection()) {
      setTenant(connection, outsider.tenant());
      JdbcHerdReportRepository reports = repository(connection);
      assertThat(reports.herdPosition(tenant(f), f.farm(), null, null, null, 20, 0).items())
          .isEmpty();
      assertThat(reports.lifecycle(tenant(f), f.farm(), FROM, TO, null, null, 20, 0).items())
          .isEmpty();
      connection.rollback();
    }
  }

  @Test
  void appliesTypedFiltersInclusiveDatesAndBoundedPaginationToEveryReport() throws Exception {
    Fixture f = fixture();
    seedFacts(f);
    try (Connection connection = adminConnection()) {
      JdbcHerdReportRepository reports = repository(connection);

      assertThat(
              reports
                  .herdPosition(
                      tenant(f),
                      f.farm(),
                      HerdReportCategory.UNCLASSIFIED,
                      HerdAnimalSex.FEMALE,
                      f.destinationPaddock(),
                      1,
                      0)
                  .items())
          .singleElement()
          .satisfies(item -> assertThat(item.animal().id()).isEqualTo(f.activeFemale()));

      assertThat(
              reports
                  .lifecycle(
                      tenant(f),
                      f.farm(),
                      LocalDate.of(2026, 2, 1),
                      LocalDate.of(2026, 2, 1),
                      LifecycleEvent.SOLD,
                      f.sold(),
                      1,
                      0)
                  .items())
          .singleElement()
          .satisfies(item -> assertThat(item.occurredOn()).isEqualTo(LocalDate.of(2026, 2, 1)));

      assertThat(
              reports
                  .movements(
                      tenant(f),
                      f.farm(),
                      LocalDate.of(2026, 3, 1),
                      LocalDate.of(2026, 3, 1),
                      f.activeFemale(),
                      f.sourcePaddock(),
                      f.destinationPaddock(),
                      1,
                      0)
                  .items())
          .hasSize(1);
      assertThat(
              reports
                  .movements(
                      tenant(f),
                      f.farm(),
                      FROM,
                      TO,
                      null,
                      f.destinationPaddock(),
                      null,
                      1,
                      0)
                  .items())
          .isEmpty();

      assertThat(
              reports
                  .transfers(
                      tenant(f), f.farm(), FROM, TO, TransferDirection.IN, null, 1, 0)
                  .items())
          .isEmpty();
      assertThat(
              reports
                  .transfers(
                      tenant(f), f.otherFarm(), FROM, TO, TransferDirection.IN, null, 1, 0)
                  .items())
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.sourceFarm()).isNull();
                assertThat(item.destinationFarm().id()).isEqualTo(f.otherFarm());
                assertThat(item.animal().identification()).isNull();
              });

      assertThat(
              reports
                  .weights(
                      tenant(f),
                      f.farm(),
                      LocalDate.of(2026, 5, 2),
                      LocalDate.of(2026, 5, 2),
                      f.activeFemale(),
                      HerdReportCategory.UNCLASSIFIED,
                      1,
                      0)
                  .items())
          .extracting(WeightItem::weight)
          .containsExactly(new BigDecimal("450.000"));

      assertThat(
              reports
                  .health(
                      tenant(f),
                      f.farm(),
                      FROM,
                      TO,
                      HealthTreatmentType.DEWORMING,
                      f.activeFemale(),
                      1,
                      0)
                  .items())
          .singleElement()
          .satisfies(
              item -> assertThat(item.treatmentType()).isEqualTo(HealthTreatmentType.DEWORMING));

      assertThat(
              reports
                  .reproduction(
                      tenant(f),
                      f.farm(),
                      FROM,
                      TO,
                      f.deceased(),
                      ReproductionServiceType.NATURAL_SERVICE,
                      PregnancyStatus.TERMINATED,
                      1,
                      0)
                  .items())
          .singleElement()
          .satisfies(
              item ->
                  assertThat(item.action()).isEqualTo(ReproductionAction.PREGNANCY_TERMINATED));

      var planner =
          reports.planner(
              tenant(f),
              f.farm(),
              FROM,
              TO,
              LocalDate.of(2026, 6, 1),
              HerdPlannerStatus.OPEN,
              HerdPlannerType.GENERAL,
              f.activeFemale(),
              1,
              1);
      assertThat(planner.totalElements()).isEqualTo(2);
      assertThat(planner.items()).hasSize(1);
    }
  }

  @Test
  void reportIndexesSupportRepresentativeBoundedPlans() throws Exception {
    Fixture f = fixture();
    seedFacts(f);
    try (Connection connection = adminConnection()) {
      execute(
          connection,
          "analyze app.animal_events; analyze app.animal_transfers; analyze app.animal_weight_measurements; analyze app.animal_health_treatments; analyze app.herd_planner_items");
      execute(connection, "set enable_seqscan=off");

      assertThat(
              explain(
                  connection,
                  "select id from app.animal_events where tenant_id=? and farm_id=? and event_type=? and occurred_on between ? and ? order by occurred_on desc,recorded_at desc,id desc limit 20",
                  f.tenant(),
                  f.farm(),
                  "BORN",
                  FROM,
                  TO))
          .contains("animal_events_report_idx", "Buffers:");
      assertThat(
              explain(
                  connection,
                  "select e.id from app.herd_movements m join app.animal_events e on e.id=m.event_id where m.tenant_id=? and m.farm_id=? and e.event_type='MOVED' and e.occurred_on between ? and ? order by e.occurred_on desc,e.recorded_at desc,e.id desc limit 20",
                  f.tenant(),
                  f.farm(),
                  FROM,
                  TO))
          .contains("herd_movements_destination_idx", "Index Scan", "Buffers:")
          .doesNotContain("Seq Scan");
      assertThat(
              explain(
                  connection,
                  "select id from app.animal_transfers where tenant_id=? and source_farm_id=? and occurred_on between ? and ? order by occurred_on desc,recorded_at desc,id desc limit 20",
                  f.tenant(),
                  f.farm(),
                  FROM,
                  TO))
          .contains("animal_transfers_source_occurred_idx", "Buffers:");
      assertThat(
              explain(
                  connection,
                  "select id from app.animal_weight_measurements where tenant_id=? and farm_id=? and measured_on between ? and ? order by measured_on desc,recorded_at desc,id desc limit 20",
                  f.tenant(),
                  f.farm(),
                  FROM,
                  TO))
          .contains("animal_weight_measurements_report_idx", "Buffers:");
      assertThat(
              explain(
                  connection,
                  "select id from app.animal_health_treatments where tenant_id=? and farm_id=? and treatment_type=? and occurred_on between ? and ? order by occurred_on desc,recorded_at desc,id desc limit 20",
                  f.tenant(),
                  f.farm(),
                  "VACCINATION",
                  FROM,
                  TO))
          .contains("animal_health_treatments_report_idx", "Buffers:");
      assertThat(
              explain(
                  connection,
                  "select id from app.herd_planner_items where tenant_id=? and farm_id=? and status=? and scheduled_for between ? and ? order by scheduled_for,created_at,id limit 20",
                  f.tenant(),
                  f.farm(),
                  "OPEN",
                  FROM,
                  TO))
          .contains("herd_planner_items_farm_agenda_idx", "Buffers:");
    }
  }

  private Fixture fixture() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    UUID otherFarm = UUID.randomUUID();
    UUID source = UUID.randomUUID();
    UUID destination = UUID.randomUUID();
    UUID activeFemale = UUID.randomUUID();
    UUID activeMale = UUID.randomUUID();
    UUID sold = UUID.randomUUID();
    UUID deceased = UUID.randomUUID();
    executeAsAdmin(
        "insert into app.organizations(id,name,status) values(?,?,'ACTIVE')", tenant, "Organização");
    executeAsAdmin(
        "insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE'),(?,?,?,'ACTIVE')",
        farm,
        tenant,
        "Fazenda Origem",
        otherFarm,
        tenant,
        "Fazenda Destino");
    executeAsAdmin(
        "insert into app.paddocks(id,tenant_id,farm_id,name,status) values(?,?,?,?,'ACTIVE'),(?,?,?,?,'ACTIVE')",
        source,
        tenant,
        farm,
        "Pasto Sul",
        destination,
        tenant,
        farm,
        "Pasto Norte");
    animal(activeFemale, tenant, farm, "A-1", "Aurora", "FEMALE", "ACTIVE", destination);
    animal(activeMale, tenant, farm, "A-2", "Bravo", "MALE", "ACTIVE", null);
    animal(sold, tenant, farm, "A-3", "Cedro", "MALE", "SOLD", null);
    animal(deceased, tenant, farm, "A-4", "Duna", "FEMALE", "DECEASED", null);
    return new Fixture(
        tenant, farm, otherFarm, source, destination, activeFemale, activeMale, sold, deceased);
  }

  private void animal(
      UUID id,
      UUID tenant,
      UUID farm,
      String identification,
      String name,
      String sex,
      String status,
      UUID paddock)
      throws Exception {
    executeAsAdmin(
        "insert into app.animals(id,tenant_id,farm_id,identification,name,sex,birth_date,status,paddock_id) values(?,?,?,?,?,?,'2024-01-01',?,?)",
        id,
        tenant,
        farm,
        identification,
        name,
        sex,
        status,
        paddock);
  }

  private void seedFacts(Fixture f) throws Exception {
    for (UUID animal : new UUID[] {f.activeFemale(), f.activeMale(), f.sold(), f.deceased()}) {
      String identification = animal.equals(f.activeFemale()) ? "A-1" : "H-" + animal;
      event(
          f,
          animal,
          "CREATED",
          LocalDate.of(2026, 1, 1),
          null,
          "{\"identification\":\"" + identification + "\",\"name\":\"Histórico\"}");
    }
    event(f, f.sold(), "SOLD", LocalDate.of(2026, 2, 1), UUID.randomUUID(), "{\"notes\":\"Venda\"}");
    event(
        f,
        f.deceased(),
        "DECEASED",
        LocalDate.of(2026, 2, 2),
        UUID.randomUUID(),
        "{\"notes\":\"Morte\"}");

    UUID movementEvent = UUID.randomUUID();
    executeAsAdmin(
        "insert into app.animal_events(id,tenant_id,farm_id,animal_id,event_type,operation_id,occurred_on,resulting_version,payload) values(?,?,?,?,'MOVED',?,'2026-03-01',1,cast(? as jsonb))",
        movementEvent,
        f.tenant(),
        f.farm(),
        f.activeFemale(),
        UUID.randomUUID(),
        "{\"sourcePaddockName\":\"Pasto Sul\",\"destinationPaddockName\":\"Pasto Norte\",\"notes\":\"Rodízio\"}");
    executeAsAdmin(
        "insert into app.herd_movements(event_id,tenant_id,farm_id,animal_id,operation_id,source_paddock_id,destination_paddock_id) values(?,?,?,?,?,?,?)",
        movementEvent,
        f.tenant(),
        f.farm(),
        f.activeFemale(),
        UUID.randomUUID(),
        f.sourcePaddock(),
        f.destinationPaddock());

    executeAsAdmin(
        "insert into app.animal_transfers(id,tenant_id,animal_id,operation_id,source_farm_id,destination_farm_id,source_farm_name,destination_farm_name,occurred_on,original_expected_version,resulting_version,notes) values(?,?,?,?,?,?,?,?,?,1,2,?)",
        UUID.randomUUID(),
        f.tenant(),
        f.activeFemale(),
        UUID.randomUUID(),
        f.farm(),
        f.otherFarm(),
        "Fazenda Origem",
        "SEGREDO DA FAZENDA DESTINO",
        LocalDate.of(2026, 4, 1),
        "Transferência");

    weight(f, "2026-05-01", "400.000");
    weight(f, "2026-05-02", "450.000");
    health(f, "VACCINATION", "Vacina A", "Protocolo A", "2026-06-01", "2027-06-01");
    health(f, "DEWORMING", "Vermífugo B", "Protocolo B", "2026-06-02", null);
    reproduction(f);
    planner(f, "OPEN", "2026-01-15", null, null);
    planner(f, "OPEN", "2026-07-15", null, null);
    planner(f, "COMPLETED", "2026-02-15", "2026-02-16T00:00:00Z", null);
    planner(f, "CANCELLED", "2026-03-15", null, "2026-03-16T00:00:00Z");
  }

  private void event(
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

  private void weight(Fixture f, String day, String value) throws Exception {
    executeAsAdmin(
        "insert into app.animal_weight_measurements(id,tenant_id,farm_id,animal_id,operation_id,measured_on,weight_kg) values(?,?,?,?,?,cast(? as date),cast(? as numeric))",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        f.activeFemale(),
        UUID.randomUUID(),
        day,
        value);
  }

  private void health(
      Fixture f, String type, String product, String protocol, String day, String due)
      throws Exception {
    executeAsAdmin(
        "insert into app.animal_health_treatments(id,tenant_id,farm_id,animal_id,operation_id,treatment_type,occurred_on,product,protocol,next_due_on) values(?,?,?,?,?,?,cast(? as date),?,?,cast(? as date))",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        f.activeFemale(),
        UUID.randomUUID(),
        type,
        day,
        product,
        protocol,
        due);
  }

  private void reproduction(Fixture f) throws Exception {
    UUID open = UUID.randomUUID();
    UUID calved = UUID.randomUUID();
    UUID ended = UUID.randomUUID();
    executeAsAdmin(
        "insert into app.animal_pregnancies(id,tenant_id,farm_id,mother_animal_id,service_type,service_on,expected_calving_on,status,ended_on,termination_reason,calf_animal_id,operation_id,command_payload) values(?,?,?,?,'INSEMINATION','2026-03-10','2026-12-20','POSSIBLE',null,null,null,?,'{}'),(?,?,?,?,'INSEMINATION','2026-01-10','2026-10-20','CALVED','2026-10-20',null,?,?,'{}'),(?,?,?,?,'NATURAL_SERVICE','2025-01-10','2025-10-20','TERMINATED','2025-02-01','OTHER',null,?,'{}')",
        open,
        f.tenant(),
        f.farm(),
        f.activeFemale(),
        UUID.randomUUID(),
        calved,
        f.tenant(),
        f.farm(),
        f.deceased(),
        f.activeMale(),
        UUID.randomUUID(),
        ended,
        f.tenant(),
        f.farm(),
        f.deceased(),
        UUID.randomUUID());
    event(f, f.deceased(), "BREEDING_RECORDED", LocalDate.of(2026, 1, 10), UUID.randomUUID(),
        "{\"pregnancyId\":\"" + calved + "\",\"serviceType\":\"INSEMINATION\",\"expectedCalvingOn\":\"2026-10-20\"}");
    event(f, f.deceased(), "PREGNANCY_CONFIRMED", LocalDate.of(2026, 2, 10), UUID.randomUUID(),
        "{\"pregnancyId\":\"" + calved + "\"}");
    event(f, f.deceased(), "PREGNANCY_ENDED", LocalDate.of(2026, 2, 11), UUID.randomUUID(),
        "{\"pregnancyId\":\"" + ended + "\",\"terminationReason\":\"OTHER\"}");
    event(f, f.deceased(), "CALVED", LocalDate.of(2026, 10, 20), UUID.randomUUID(),
        "{\"pregnancyId\":\"" + calved + "\",\"calfAnimalId\":\"" + f.activeMale() + "\"}");
    event(f, f.activeMale(), "BORN", LocalDate.of(2026, 10, 20), UUID.randomUUID(),
        "{\"pregnancyId\":\"" + calved + "\",\"motherAnimalId\":\"" + f.deceased() + "\"}");
  }

  private void planner(Fixture f, String status, String day, String completed, String cancelled)
      throws Exception {
    executeAsAdmin(
        "insert into app.herd_planner_items(id,tenant_id,farm_id,animal_id,type,title,scheduled_for,status,created_by,completed_at,cancelled_at) values(?,?,?,?,?,'Manejo',cast(? as date),?,?,cast(? as timestamptz),cast(? as timestamptz))",
        UUID.randomUUID(),
        f.tenant(),
        f.farm(),
        f.activeFemale(),
        "GENERAL",
        day,
        status,
        UUID.randomUUID(),
        completed,
        cancelled);
  }

  private JdbcHerdReportRepository repository(Connection connection) {
    return new JdbcHerdReportRepository(
        new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
  }

  private static TenantId tenant(Fixture fixture) {
    return new TenantId(fixture.tenant());
  }

  private long factCount() throws Exception {
    try (Connection connection = adminConnection();
        var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "select (select count(*) from app.animals)+(select count(*) from app.animal_events)+(select count(*) from app.herd_movements)+(select count(*) from app.animal_transfers)+(select count(*) from app.animal_weight_measurements)+(select count(*) from app.animal_health_treatments)+(select count(*) from app.animal_pregnancies)+(select count(*) from app.herd_planner_items)")) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String explain(Connection connection, String sql, Object... parameters)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement("explain (analyze,buffers,format text) " + sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      try (ResultSet result = statement.executeQuery()) {
        StringBuilder plan = new StringBuilder();
        while (result.next()) {
          plan.append(result.getString(1)).append('\n');
        }
        return plan.toString();
      }
    }
  }

  private record Fixture(
      UUID tenant,
      UUID farm,
      UUID otherFarm,
      UUID sourcePaddock,
      UUID destinationPaddock,
      UUID activeFemale,
      UUID activeMale,
      UUID sold,
      UUID deceased) {}
}
