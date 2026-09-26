package com.gerenciadorrural.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.HerdAnimalNotFoundException;
import com.gerenciadorrural.modules.herd.application.HerdLifecycleConflictException;
import com.gerenciadorrural.modules.herd.application.HerdOperationIdempotencyConflictException;
import com.gerenciadorrural.modules.herd.application.ManageHerdIntelligence;
import com.gerenciadorrural.modules.herd.application.RetractHealthTreatment;
import com.gerenciadorrural.modules.herd.domain.AnimalEvent;
import com.gerenciadorrural.modules.herd.domain.AnimalEventDetails;
import com.gerenciadorrural.modules.herd.domain.AnimalEventDetails.HealthTreatmentRetractionEventDetails;
import com.gerenciadorrural.modules.herd.domain.AnimalEventRepository;
import com.gerenciadorrural.modules.herd.domain.AnimalEventType;
import com.gerenciadorrural.modules.herd.domain.HealthTreatmentEventDetails;
import com.gerenciadorrural.modules.herd.domain.HealthTreatmentType;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.HealthTreatmentRetraction;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcAnimalEventRepository;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalProfileRepository;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdManagementRepository;
import com.gerenciadorrural.shared.infrastructure.database.DatabaseAccessProperties;
import com.gerenciadorrural.shared.infrastructure.database.SpringTenantTransactionExecutor;
import com.gerenciadorrural.shared.infrastructure.database.TransactionalDatabaseRole;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetractHealthTreatmentIntegrationTest extends PostgresMigrationTestSupport {
  private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");
  private HikariDataSource dataSource;
  private SpringTenantTransactionExecutor transactions;
  private JdbcHerdManagementRepository herd;
  private JdbcHerdAnimalProfileRepository animals;
  private JdbcAnimalEventRepository events;
  private RetractHealthTreatment service;
  private ManageHerdIntelligence management;

  @BeforeEach
  void setUpRuntime() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(POSTGRES.getJdbcUrl());
    config.setUsername(POSTGRES.getUsername());
    config.setPassword(POSTGRES.getPassword());
    config.setMaximumPoolSize(4);
    config.setMinimumIdle(0);
    dataSource = new HikariDataSource(config);
    var named = new NamedParameterJdbcTemplate(dataSource);
    transactions =
        new SpringTenantTransactionExecutor(
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
            named,
            new TransactionalDatabaseRole(
                new JdbcTemplate(dataSource), new DatabaseAccessProperties("app", "app_api")));
    herd = new JdbcHerdManagementRepository(named);
    animals = new JdbcHerdAnimalProfileRepository(named);
    events = new JdbcAnimalEventRepository(named, new ObjectMapper().findAndRegisterModules());
    service =
        new RetractHealthTreatment(
            transactions, herd, animals, events, Clock.fixed(NOW, ZoneOffset.UTC));
    management =
        new ManageHerdIntelligence(
            transactions, animals, herd, events, new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void closeRuntime() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  @Test
  void persistsAppendOnlyFactAndAuditOnceAndReplaysCanonicalIntent() throws Exception {
    Fixture fixture = fixture();
    UUID treatment = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 20));
    UUID operation = UUID.randomUUID();

    HealthTreatmentRetraction created =
        service.retract(fixture.context(), fixture.animal(), treatment, operation, "  Data incorreta  ");
    HealthTreatmentRetraction replayed =
        service.retract(fixture.context(), fixture.animal(), treatment, operation, "Data incorreta");

    assertThat(replayed).isEqualTo(created);
    assertThat(created.reason()).isEqualTo("Data incorreta");
    assertThat(created.actorUserId()).isEqualTo(fixture.context().userId());
    assertThat(count("app.animal_health_treatment_retractions", "operation_id", operation)).isOne();
    assertThat(count("app.animal_events", "operation_id", operation)).isOne();
    assertThat(count("app.animal_health_treatments", "id", treatment)).isOne();

    List<AnimalEvent> history =
        transactions.execute(
            fixture.context(),
            () ->
                events.history(
                    fixture.context().tenantId(),
                    fixture.context().farmId(),
                    fixture.animal(),
                    AnimalEventType.HEALTH_TREATMENT_RETRACTED,
                    10,
                    0));
    assertThat(history).singleElement().satisfies(event -> {
      assertThat(event.operationId()).isEqualTo(operation);
      assertThat(event.actorUserId()).isEqualTo(fixture.context().userId());
      assertThat(event.occurredOn()).isEqualTo(LocalDate.of(2026, 9, 22));
      assertThat(event.resultingVersion()).isEqualTo(4);
      assertThat(event.details())
          .isEqualTo(
              new HealthTreatmentRetractionEventDetails(
                  treatment, created.id(), "Data incorreta"));
    });

    assertThat(
            transactions.execute(
                fixture.context(),
                () ->
                    herd.treatments(
                        fixture.context().tenantId(),
                        fixture.context().farmId(),
                        fixture.animal(),
                        10,
                        0)))
        .extracting(treatmentRow -> treatmentRow.id())
        .containsExactly(treatment);
  }

  @Test
  void acceptsNullAndBlankReasonAsTheSameCanonicalIntent() throws Exception {
    Fixture fixture = fixture();
    UUID treatment = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 20));
    UUID operation = UUID.randomUUID();

    HealthTreatmentRetraction created =
        service.retract(fixture.context(), fixture.animal(), treatment, operation, null);
    HealthTreatmentRetraction replay =
        service.retract(fixture.context(), fixture.animal(), treatment, operation, "   ");

    assertThat(created.reason()).isNull();
    assertThat(replay).isEqualTo(created);
    assertThat(count("app.animal_events", "operation_id", operation)).isOne();
  }

  @Test
  void rejectsIdempotencyConflictsAndNewOperationForRetractedTarget() throws Exception {
    Fixture fixture = fixture();
    UUID first = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 20));
    UUID second = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 21));
    UUID operation = UUID.randomUUID();
    service.retract(fixture.context(), fixture.animal(), first, operation, "motivo");

    assertThatThrownBy(
            () -> service.retract(fixture.context(), fixture.animal(), first, operation, "outro"))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);
    assertThatThrownBy(
            () -> service.retract(fixture.context(), fixture.animal(), second, operation, "motivo"))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);
    assertThatThrownBy(
            () ->
                service.retract(
                    fixture.context(), fixture.animal(), first, UUID.randomUUID(), "motivo"))
        .isInstanceOf(HerdLifecycleConflictException.class);

    assertThat(count("app.animal_health_treatment_retractions", "treatment_id", first)).isOne();
    assertThat(count("app.animal_events", "operation_id", operation)).isOne();
  }

  @Test
  void rejectsReuseOfTheOriginalTreatmentOperation() throws Exception {
    Fixture fixture = fixture();
    UUID treatment = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 20));
    UUID originalOperation = treatmentOperation(treatment);

    assertThatThrownBy(
            () ->
                service.retract(
                    fixture.context(),
                    fixture.animal(),
                    treatment,
                    originalOperation,
                    "motivo"))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);

    assertThat(count("app.animal_health_treatment_retractions", "treatment_id", treatment)).isZero();
    assertThat(count("app.animal_events", "operation_id", originalOperation)).isZero();
  }

  @Test
  void creationOperationCannotBeReusedForRetraction() throws Exception {
    Fixture fixture = fixture();
    UUID operation = UUID.randomUUID();
    createHealth(fixture, operation, 4, LocalDate.of(2026, 8, 20));
    UUID treatment = treatmentFor(fixture, operation);

    assertThat(createHealth(fixture, operation, 4, LocalDate.of(2026, 8, 20)).replayed())
        .isTrue();
    assertThatThrownBy(
            () -> management.health(
                fixture.context(), operation, HealthTreatmentType.VACCINATION, null,
                LocalDate.of(2026, 8, 20), "Outra vacina", null, null, null,
                List.of(new ManageHerdIntelligence.BasicAnimalCommand(fixture.animal(), 4))))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);

    assertThatThrownBy(
            () -> service.retract(fixture.context(), fixture.animal(), treatment, operation, "motivo"))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);

    assertThat(count("app.animal_health_treatment_retractions", "treatment_id", treatment)).isZero();
    assertThat(count("app.animal_events", "operation_id", operation)).isOne();
    assertThat(count("app.animal_health_treatments", "id", treatment)).isOne();
  }

  @Test
  void retractionOperationCannotBeReusedForHealthCreation() throws Exception {
    Fixture fixture = fixture();
    UUID creationOperation = UUID.randomUUID();
    UUID retractionOperation = UUID.randomUUID();
    createHealth(fixture, creationOperation, 4, LocalDate.of(2026, 8, 20));
    UUID originalTreatment = treatmentFor(fixture, creationOperation);
    var retraction = service.retract(
        fixture.context(), fixture.animal(), originalTreatment, retractionOperation, "motivo");

    assertThatThrownBy(
            () -> createHealth(fixture, retractionOperation, 5, LocalDate.of(2026, 8, 21)))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);

    assertThat(count("app.animal_health_treatments", "animal_id", fixture.animal())).isOne();
    assertThat(count("app.herd_management_operations", "operation_id", retractionOperation)).isZero();
    assertThat(count("app.animal_events", "operation_id", retractionOperation)).isOne();
    assertThat(count("app.animal_health_treatment_retractions", "operation_id", retractionOperation))
        .isOne();
    assertThat(transactions.execute(
            fixture.context(),
            () -> herd.findHealthTreatmentRetractionByOperation(
                fixture.context().tenantId(), fixture.context().farmId(), retractionOperation)))
        .contains(retraction);
  }

  @Test
  void concurrentHealthCreationAndRetractionWithSameOperationSerializeToOneWinner()
      throws Exception {
    Fixture fixture = fixture();
    UUID originalOperation = UUID.randomUUID();
    UUID competingOperation = UUID.randomUUID();
    createHealth(fixture, originalOperation, 4, LocalDate.of(2026, 8, 20));
    UUID treatment = treatmentFor(fixture, originalOperation);
    CountDownLatch start = new CountDownLatch(1);
    ConcurrentLinkedQueue<String> successes = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    pool.submit(() -> {
      try {
        start.await();
        createHealth(fixture, competingOperation, 5, LocalDate.of(2026, 8, 21));
        successes.add("creation");
      } catch (Throwable failure) {
        failures.add(failure);
      }
    });
    pool.submit(() -> {
      try {
        start.await();
        service.retract(fixture.context(), fixture.animal(), treatment, competingOperation, null);
        successes.add("retraction");
      } catch (Throwable failure) {
        failures.add(failure);
      }
    });

    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
    assertThat(successes).hasSize(1);
    assertThat(failures).singleElement()
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);
    assertThat(count("app.animal_events", "operation_id", competingOperation)).isOne();
    assertThat(count("app.herd_management_operations", "operation_id", competingOperation)
        + count("app.animal_health_treatment_retractions", "operation_id", competingOperation))
        .isOne();
  }

  private ManageHerdIntelligence.Result createHealth(
      Fixture fixture, UUID operation, long expectedVersion, LocalDate day) {
    return management.health(
        fixture.context(), operation, HealthTreatmentType.VACCINATION, null, day,
        "Vacina", null, null, null,
        List.of(new ManageHerdIntelligence.BasicAnimalCommand(fixture.animal(), expectedVersion)));
  }

  private UUID treatmentFor(Fixture fixture, UUID operation) {
    return transactions.execute(
        fixture.context(),
        () -> herd.treatments(
                fixture.context().tenantId(), fixture.context().farmId(), fixture.animal(), 10, 0)
            .stream()
            .filter(treatment -> operation.equals(treatment.operationId()))
            .findFirst()
            .orElseThrow()
            .id());
  }

  @Test
  void rejectsUnknownAndCrossScopeTargetsAsNotFound() throws Exception {
    Fixture own = fixture();
    UUID otherAnimal = seedAnimal(own.tenant(), own.farm(), 0);
    UUID treatmentOfOtherAnimal =
        seedTreatment(own, otherAnimal, LocalDate.of(2026, 8, 20));
    Fixture otherTenant = fixture();
    UUID treatmentOfOtherTenant =
        seedTreatment(otherTenant, otherTenant.animal(), LocalDate.of(2026, 8, 20));

    assertThatThrownBy(
            () ->
                service.retract(
                    own.context(), own.animal(), UUID.randomUUID(), UUID.randomUUID(), null))
        .isInstanceOf(HerdAnimalNotFoundException.class);
    assertThatThrownBy(
            () ->
                service.retract(
                    own.context(), own.animal(), treatmentOfOtherAnimal, UUID.randomUUID(), null))
        .isInstanceOf(HerdAnimalNotFoundException.class);
    assertThatThrownBy(
            () ->
                service.retract(
                    own.context(), own.animal(), treatmentOfOtherTenant, UUID.randomUUID(), null))
        .isInstanceOf(HerdAnimalNotFoundException.class);

    assertThat(countAll("app.animal_health_treatment_retractions")).isZero();
  }

  @Test
  void rollsBackRetractionWhenAuditAppendFails() throws Exception {
    Fixture fixture = fixture();
    UUID treatment = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 20));
    UUID operation = UUID.randomUUID();
    IllegalStateException failure = new IllegalStateException("falha controlada no evento");
    AnimalEventRepository failingEvents = failingAfterLock(events, failure);
    RetractHealthTreatment failingService =
        new RetractHealthTreatment(
            transactions,
            herd,
            animals,
            failingEvents,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                failingService.retract(
                    fixture.context(), fixture.animal(), treatment, operation, "motivo"))
        .isSameAs(failure);

    assertThat(count("app.animal_health_treatment_retractions", "operation_id", operation)).isZero();
    assertThat(count("app.animal_events", "operation_id", operation)).isZero();
    assertThat(count("app.animal_health_treatments", "id", treatment)).isOne();
  }

  @Test
  void concurrentDifferentOperationsCreateOneRetractionAndOneEvent() throws Exception {
    Fixture fixture = fixture();
    UUID treatment = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 20));
    UUID firstOperation = UUID.randomUUID();
    UUID secondOperation = UUID.randomUUID();
    CountDownLatch start = new CountDownLatch(1);
    ConcurrentLinkedQueue<HealthTreatmentRetraction> successes = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    pool.submit(
        () -> runConcurrent(fixture, treatment, firstOperation, start, successes, failures));
    pool.submit(
        () -> runConcurrent(fixture, treatment, secondOperation, start, successes, failures));

    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

    assertThat(successes).hasSize(1);
    assertThat(failures).singleElement().isInstanceOf(HerdLifecycleConflictException.class);
    assertThat(count("app.animal_health_treatment_retractions", "treatment_id", treatment)).isOne();
    assertThat(countAll("app.animal_events")).isOne();
  }

  @Test
  void oldAndRetractionHealthEventsBothRoundTrip() throws Exception {
    Fixture fixture = fixture();
    UUID treatment = seedTreatment(fixture, fixture.animal(), LocalDate.of(2026, 8, 20));
    UUID oldOperation = UUID.randomUUID();
    UUID retractionOperation = UUID.randomUUID();
    HealthTreatmentEventDetails oldDetails =
        new HealthTreatmentEventDetails(
            HealthTreatmentType.VACCINATION, null, "Produto", "Protocolo", null, "Observação");

    transactions.execute(
        fixture.context(),
        () ->
            new JdbcTemplate(dataSource)
                .update(
                    """
                    insert into app.animal_events
                      (id,tenant_id,farm_id,animal_id,event_type,operation_id,actor_user_id,
                       occurred_on,resulting_version,payload)
                    values(?,?,?,?,'HEALTH_TREATMENT',?,?,?,4,cast(? as jsonb))
                    """,
                    UUID.randomUUID(),
                    fixture.tenant(),
                    fixture.farm(),
                    fixture.animal(),
                    oldOperation,
                    fixture.context().userId(),
                    LocalDate.of(2026, 8, 20),
                    """
                    {"treatmentType":"VACCINATION","product":"Produto","protocol":"Protocolo",
                     "nextDueOn":null,"notes":"Observação"}
                    """));
    HealthTreatmentRetraction retraction =
        service.retract(
            fixture.context(), fixture.animal(), treatment, retractionOperation, "duplicado");

    List<AnimalEvent> history =
        transactions.execute(
            fixture.context(),
            () ->
                events.history(
                    fixture.context().tenantId(),
                    fixture.context().farmId(),
                    fixture.animal(),
                    null,
                    10,
                    0));
    assertThat(history)
        .extracting(AnimalEvent::type)
        .containsExactly(
            AnimalEventType.HEALTH_TREATMENT_RETRACTED, AnimalEventType.HEALTH_TREATMENT);
    assertThat(history.get(0).details())
        .isEqualTo(
            new HealthTreatmentRetractionEventDetails(
                treatment, retraction.id(), "duplicado"));
    assertThat(history.get(1).details()).isEqualTo(oldDetails);
  }

  private void runConcurrent(
      Fixture fixture,
      UUID treatment,
      UUID operation,
      CountDownLatch start,
      ConcurrentLinkedQueue<HealthTreatmentRetraction> successes,
      ConcurrentLinkedQueue<Throwable> failures) {
    try {
      assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
      successes.add(
          service.retract(fixture.context(), fixture.animal(), treatment, operation, null));
    } catch (Throwable failure) {
      failures.add(failure);
    }
  }

  private AnimalEventRepository failingAfterLock(
      AnimalEventRepository delegate, RuntimeException failure) {
    return new AnimalEventRepository() {
      @Override
      public void record(
          TenantId tenantId,
          UUID farmId,
          UUID animalId,
          AnimalEventType type,
          UUID operationId,
          UUID actorUserId,
          LocalDate occurredOn,
          long resultingVersion,
          AnimalEventDetails details) {
        throw failure;
      }

      @Override
      public Optional<AnimalEvent> findByOperation(TenantId tenantId, UUID farmId, UUID operationId) {
        return delegate.findByOperation(tenantId, farmId, operationId);
      }

      @Override
      public List<AnimalEvent> history(
          TenantId tenantId,
          UUID farmId,
          UUID animalId,
          AnimalEventType type,
          int size,
          long offset) {
        return delegate.history(tenantId, farmId, animalId, type, size, offset);
      }

      @Override
      public long count(
          TenantId tenantId, UUID farmId, UUID animalId, AnimalEventType type) {
        return delegate.count(tenantId, farmId, animalId, type);
      }

      @Override
      public void lockOperation(TenantId tenantId, UUID farmId, UUID operationId) {
        delegate.lockOperation(tenantId, farmId, operationId);
      }
    };
  }

  private Fixture fixture() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    executeAsAdmin(
        "insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",
        tenant,
        "Organização");
    executeAsAdmin(
        "insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
        farm,
        tenant,
        "Fazenda");
    UUID animal = seedAnimal(tenant, farm, 4);
    TenantContext context =
        new TenantContext(
            new TenantId(tenant), actor, farm, UUID.randomUUID(), "OPERATOR", "ALL_FARMS");
    return new Fixture(tenant, farm, animal, context);
  }

  private UUID seedAnimal(UUID tenant, UUID farm, long version) throws Exception {
    UUID animal = UUID.randomUUID();
    executeAsAdmin(
        """
        insert into app.animals
          (id,tenant_id,farm_id,identification,sex,birth_date,status,version)
        values(?,?,?,?,'FEMALE',?,'ACTIVE',?)
        """,
        animal,
        tenant,
        farm,
        "B-" + animal.toString().substring(0, 8),
        LocalDate.of(2025, 1, 1),
        version);
    return animal;
  }

  private UUID seedTreatment(Fixture fixture, UUID animal, LocalDate occurredOn) throws Exception {
    UUID treatment = UUID.randomUUID();
    executeAsAdmin(
        """
        insert into app.animal_health_treatments
          (id,tenant_id,farm_id,animal_id,operation_id,treatment_type,occurred_on,actor_user_id)
        values(?,?,?,?,?,'VACCINATION',?,?)
        """,
        treatment,
        fixture.tenant(),
        fixture.farm(),
        animal,
        UUID.randomUUID(),
        occurredOn,
        fixture.context().userId());
    return treatment;
  }

  private long count(String table, String column, UUID value) throws Exception {
    try (Connection connection = adminConnection();
        PreparedStatement statement =
            connection.prepareStatement("select count(*) from " + table + " where " + column + "=?")) {
      statement.setObject(1, value);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private long countAll(String table) throws Exception {
    try (Connection connection = adminConnection();
        PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
        ResultSet result = statement.executeQuery()) {
      result.next();
      return result.getLong(1);
    }
  }

  private UUID treatmentOperation(UUID treatment) throws Exception {
    try (Connection connection = adminConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select operation_id from app.animal_health_treatments where id=?")) {
      statement.setObject(1, treatment);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getObject(1, UUID.class);
      }
    }
  }

  private record Fixture(UUID tenant, UUID farm, UUID animal, TenantContext context) {
  }
}
