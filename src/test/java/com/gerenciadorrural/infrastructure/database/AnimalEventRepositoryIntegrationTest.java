package com.gerenciadorrural.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.domain.AnimalEvent;
import com.gerenciadorrural.modules.herd.domain.AnimalEventType;
import com.gerenciadorrural.modules.herd.domain.CorrectedEventDetails;
import com.gerenciadorrural.modules.herd.domain.CreatedEventDetails;
import com.gerenciadorrural.modules.herd.domain.FieldChange;
import com.gerenciadorrural.modules.herd.domain.LifecycleEventDetails;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcAnimalEventRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnimalEventRepositoryIntegrationTest extends PostgresMigrationTestSupport {

    @Test
    void roundTripsAllTypedEventsOrdersPagesAndPreservesExactSpecialCharacters() throws Exception {
        Context context = context("A", "A1");
        UUID actor = UUID.randomUUID();
        UUID saleOperation = UUID.randomUUID();
        UUID deathOperation = UUID.randomUUID();
        CreatedEventDetails createdDetails = new CreatedEventDetails("CR-\"\\\n\tá", "Nome", "FEMALE", LocalDate.of(2024, 1, 2));
        LinkedHashMap<String, FieldChange> changes = new LinkedHashMap<>();
        changes.put("name", new FieldChange("Antes \"\\\n\tá", "Depois \"\\\n\tê"));
        CorrectedEventDetails correctedDetails = new CorrectedEventDetails(changes);
        LifecycleEventDetails soldDetails = new LifecycleEventDetails("Animal chamado \"Brisa\"\\com quebra real\ncom tab real\t e ação");
        LifecycleEventDetails deceasedDetails = new LifecycleEventDetails("Óbito \"Brisa\"\\com quebra real\ncom tab real\t e ação");

        try (Connection connection = apiConnection()) {
            setTenant(connection, context.tenant());
            JdbcAnimalEventRepository repository = repository(connection);
            TenantId tenant = new TenantId(context.tenant());
            repository.record(tenant, context.farm(), context.animal(), AnimalEventType.CREATED, null, actor, null, 0, createdDetails);
            repository.record(tenant, context.farm(), context.animal(), AnimalEventType.CORRECTED, null, actor, null, 1, correctedDetails);
            repository.record(tenant, context.farm(), context.animal(), AnimalEventType.SOLD, saleOperation, actor, LocalDate.of(2026, 9, 8), 2, soldDetails);
            repository.record(tenant, context.farm(), context.animal(), AnimalEventType.DECEASED, deathOperation, actor, LocalDate.of(2026, 9, 7), 3, deceasedDetails);
            connection.commit();
        }

        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant correctedAt = Instant.parse("2026-01-02T00:00:00Z");
        Instant tieAt = Instant.parse("2026-01-03T00:00:00Z");
        setRecordedAt(context.animal(), "CREATED", createdAt.toString());
        setRecordedAt(context.animal(), "CORRECTED", correctedAt.toString());
        setRecordedAt(context.animal(), "SOLD", tieAt.toString());
        setRecordedAt(context.animal(), "DECEASED", tieAt.toString());
        setEventId(context.animal(), "SOLD", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        setEventId(context.animal(), "DECEASED", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        List<AnimalEventType> expectedTieOrder = List.of(AnimalEventType.DECEASED, AnimalEventType.SOLD);

        try (Connection connection = apiConnection()) {
            setTenant(connection, context.tenant());
            JdbcAnimalEventRepository repository = repository(connection);
            TenantId tenant = new TenantId(context.tenant());

            List<AnimalEvent> firstPage = repository.history(tenant, context.farm(), context.animal(), null, 2, 0);
            List<AnimalEvent> secondPage = repository.history(tenant, context.farm(), context.animal(), null, 2, 2);
            List<AnimalEvent> all = repository.history(tenant, context.farm(), context.animal(), null, 10, 0);
            assertThat(firstPage).extracting(AnimalEvent::type).containsExactlyElementsOf(expectedTieOrder);
            assertThat(secondPage).extracting(AnimalEvent::type).containsExactly(AnimalEventType.CORRECTED, AnimalEventType.CREATED);
            assertThat(all).extracting(AnimalEvent::type).containsExactly(expectedTieOrder.get(0), expectedTieOrder.get(1), AnimalEventType.CORRECTED, AnimalEventType.CREATED);
            assertThat(repository.count(tenant, context.farm(), context.animal(), null)).isEqualTo(4);
            assertThat(repository.count(tenant, context.farm(), context.animal(), AnimalEventType.SOLD)).isOne();

            Map<AnimalEventType, AnimalEvent> events = all.stream().collect(java.util.stream.Collectors.toMap(AnimalEvent::type, event -> event));
            assertEvent(events.get(AnimalEventType.CREATED), context.animal(), AnimalEventType.CREATED, null, actor, null, createdAt, 0, createdDetails);
            assertEvent(events.get(AnimalEventType.CORRECTED), context.animal(), AnimalEventType.CORRECTED, null, actor, null, correctedAt, 1, correctedDetails);
            assertEvent(events.get(AnimalEventType.SOLD), context.animal(), AnimalEventType.SOLD, saleOperation, actor, LocalDate.of(2026, 9, 8), tieAt, 2, soldDetails);
            assertEvent(events.get(AnimalEventType.DECEASED), context.animal(), AnimalEventType.DECEASED, deathOperation, actor, LocalDate.of(2026, 9, 7), tieAt, 3, deceasedDetails);
            assertThat(repository.history(tenant, context.farm(), context.animal(), AnimalEventType.SOLD, 10, 0)).containsExactly(events.get(AnimalEventType.SOLD));
        }
    }

    @Test
    void scopesHistoryAndIdempotencyByTenantFarmAndAnimalUnderRls() throws Exception {
        Context a1 = context("A", "A1");
        Context a2 = context(a1.tenant(), "A2");
        Context b1 = context("B", "B1");
        UUID operation = UUID.randomUUID();
        record(a1, operation, "A1");
        record(a2, operation, "A2");
        record(b1, operation, "B1");

        try (Connection connection = apiConnection()) {
            setTenant(connection, a1.tenant());
            JdbcAnimalEventRepository repository = repository(connection);
            TenantId tenant = new TenantId(a1.tenant());
            assertThat(repository.findByOperation(tenant, a1.farm(), operation).orElseThrow().animalId()).isEqualTo(a1.animal());
            assertThat(repository.history(tenant, a1.farm(), a1.animal(), null, 10, 0)).hasSize(1);
            assertThat(repository.history(tenant, a1.farm(), a2.animal(), null, 10, 0)).isEmpty();
            assertThat(repository.findByOperation(tenant, a2.farm(), operation).orElseThrow().animalId()).isEqualTo(a2.animal());
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, b1.tenant());
            JdbcAnimalEventRepository repository = repository(connection);
            assertThat(repository.findByOperation(new TenantId(b1.tenant()), b1.farm(), operation).orElseThrow().animalId()).isEqualTo(b1.animal());
        }
    }

    private static void assertEvent(AnimalEvent actual, UUID animalId, AnimalEventType type, UUID operationId, UUID actorUserId, LocalDate occurredOn, Instant recordedAt, long resultingVersion, Object details) {
        assertThat(actual.animalId()).isEqualTo(animalId);
        assertThat(actual.type()).isEqualTo(type);
        assertThat(actual.operationId()).isEqualTo(operationId);
        assertThat(actual.actorUserId()).isEqualTo(actorUserId);
        assertThat(actual.occurredOn()).isEqualTo(occurredOn);
        assertThat(actual.recordedAt()).isEqualTo(recordedAt);
        assertThat(actual.resultingVersion()).isEqualTo(resultingVersion);
        assertThat(actual.details()).isEqualTo(details);
    }

    private void record(Context context, UUID operation, String notes) throws Exception {
        try (Connection connection = apiConnection()) {
            setTenant(connection, context.tenant());
            repository(connection).record(new TenantId(context.tenant()), context.farm(), context.animal(), AnimalEventType.SOLD, operation, UUID.randomUUID(), LocalDate.of(2026, 9, 8), 1, new LifecycleEventDetails(notes));
            connection.commit();
        }
    }

    private Context context(String tenantName, String farmName) throws Exception {
        return context(UUID.randomUUID(), tenantName, farmName);
    }

    private Context context(UUID tenant, String farmName) throws Exception {
        return context(tenant, "A", farmName);
    }

    private Context context(UUID tenant, String tenantName, String farmName) throws Exception {
        try (Connection connection = adminConnection(); var statement = connection.prepareStatement("insert into app.organizations(id,name,status) values(? ,?,'ACTIVE') on conflict(id) do nothing;insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE');insert into app.animals(id,tenant_id,farm_id,identification,sex,status) values(?,?,?,?,?,'ACTIVE')")) {
            UUID farm = UUID.randomUUID();
            UUID animal = UUID.randomUUID();
            statement.setObject(1, tenant);
            statement.setString(2, tenantName);
            statement.setObject(3, farm);
            statement.setObject(4, tenant);
            statement.setString(5, farmName);
            statement.setObject(6, animal);
            statement.setObject(7, tenant);
            statement.setObject(8, farm);
            statement.setString(9, "EV-" + animal);
            statement.setString(10, "FEMALE");
            statement.executeUpdate();
            return new Context(tenant, farm, animal);
        }
    }

    private void setRecordedAt(UUID animal, String type, String timestamp) throws Exception {
        executeAsAdmin("update app.animal_events set recorded_at=?::timestamptz where animal_id=? and event_type=?", timestamp, animal, type);
    }

    private void setEventId(UUID animal, String type, UUID id) throws Exception {
        executeAsAdmin("update app.animal_events set id=? where animal_id=? and event_type=?", id, animal, type);
    }

    private JdbcAnimalEventRepository repository(Connection connection) {
        return new JdbcAnimalEventRepository(new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)), new ObjectMapper().findAndRegisterModules());
    }

    private record Context(UUID tenant, UUID farm, UUID animal) {
    }
}
