package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.HealthTreatmentRetraction;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.HealthTreatmentRetractionWriteConflictException;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdManagementRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthTreatmentRetractionRepositoryIntegrationTest extends PostgresMigrationTestSupport {

    @Test
    void insertsRetractionAndKeepsOriginalUntouched() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID treatment = seedTreatment(fixture, animal, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 7, 22));
        UUID operation = UUID.randomUUID();

        try (Connection connection = adminConnection()) {
            JdbcHerdManagementRepository repository = repository(connection);
            HealthTreatmentRetraction created = repository.insertHealthTreatmentRetraction(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, treatment, operation,
                    "Data incorreta", fixture.actor());

            assertThat(created.id()).isNotNull();
            assertThat(created.operationId()).isEqualTo(operation);
            assertThat(created.animalId()).isEqualTo(animal);
            assertThat(created.treatmentId()).isEqualTo(treatment);
            assertThat(created.reason()).isEqualTo("Data incorreta");
            assertThat(created.actorUserId()).isEqualTo(fixture.actor());
            assertThat(created.retractedAt()).isNotNull();

            assertThat(repository.findHealthTreatmentRetractionByTreatment(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, treatment))
                    .contains(created);
            assertThat(repository.findHealthTreatmentRetractionByOperation(
                    new TenantId(fixture.tenant()), fixture.farm(), operation))
                    .contains(created);

            assertThat(repository.treatments(new TenantId(fixture.tenant()), fixture.farm(), animal, 10, 0))
                    .extracting(t -> t.id().toString() + t.occurredOn().toString())
                    .containsExactly(treatment + "2026-07-22");
            assertThat(repository.treatmentCount(new TenantId(fixture.tenant()), fixture.farm(), animal)).isOne();
        }
    }

    @Test
    void supportsLegacyNullProcedureAndNullReason() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID legacy = seedTreatment(fixture, animal, "VACCINATION", null, LocalDate.of(2026, 7, 22));

        try (Connection connection = adminConnection()) {
            JdbcHerdManagementRepository repository = repository(connection);
            HealthTreatmentRetraction created = repository.insertHealthTreatmentRetraction(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, legacy, UUID.randomUUID(),
                    null, fixture.actor());

            assertThat(created.reason()).isNull();
            assertThat(repository.findHealthTreatmentRetractionByTreatment(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, legacy))
                    .contains(created);
        }
    }

    @Test
    void rejectsSecondRetractionForSameTarget() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID treatment = seedTreatment(fixture, animal, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 7, 22));

        try (Connection connection = adminConnection()) {
            JdbcHerdManagementRepository repository = repository(connection);
            repository.insertHealthTreatmentRetraction(new TenantId(fixture.tenant()), fixture.farm(), animal,
                    treatment, UUID.randomUUID(), null, fixture.actor());

            assertThatThrownBy(() -> repository.insertHealthTreatmentRetraction(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, treatment, UUID.randomUUID(),
                    null, fixture.actor()))
                    .isInstanceOfSatisfying(
                            HealthTreatmentRetractionWriteConflictException.class,
                            conflict -> assertThat(conflict.type())
                                    .isEqualTo(HealthTreatmentRetractionWriteConflictException.Type.TARGET));
        }
    }

    @Test
    void rejectsDuplicateOperationInSameScope() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID first = seedTreatment(fixture, animal, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 7, 22));
        UUID second = seedTreatment(fixture, animal, "DEWORMING", null, LocalDate.of(2026, 7, 23));
        UUID operation = UUID.randomUUID();

        try (Connection connection = adminConnection()) {
            JdbcHerdManagementRepository repository = repository(connection);
            repository.insertHealthTreatmentRetraction(new TenantId(fixture.tenant()), fixture.farm(), animal,
                    first, operation, null, fixture.actor());

            assertThatThrownBy(() -> repository.insertHealthTreatmentRetraction(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, second, operation,
                    null, fixture.actor()))
                    .isInstanceOfSatisfying(
                            HealthTreatmentRetractionWriteConflictException.class,
                            conflict -> assertThat(conflict.type())
                                    .isEqualTo(HealthTreatmentRetractionWriteConflictException.Type.OPERATION));
        }
    }

    @Test
    void rejectsCrossScopeTargetUnderRls() throws Exception {
        Fixture own = fixture();
        UUID ownAnimal = seedAnimal(own);
        Fixture other = fixture();
        UUID otherAnimal = seedAnimal(other);
        UUID otherTreatment = seedTreatment(other, otherAnimal, "VACCINATION", "BRUCELLOSIS",
                LocalDate.of(2026, 7, 22));

        try (Connection connection = apiConnection()) {
            setTenant(connection, own.tenant());
            JdbcHerdManagementRepository repository = repository(connection);

            assertThatThrownBy(() -> repository.insertHealthTreatmentRetraction(
                    new TenantId(own.tenant()), own.farm(), ownAnimal, otherTreatment, UUID.randomUUID(),
                    null, own.actor()))
                    .isInstanceOf(DataAccessException.class);
            connection.rollback();
        }
    }

    @Test
    void appApiCanInsertValidRetraction() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID treatment = seedTreatment(fixture, animal, "VACCINATION", "BRUCELLOSIS",
                LocalDate.of(2026, 7, 22));

        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            JdbcHerdManagementRepository repository = repository(connection);
            HealthTreatmentRetraction created = repository.insertHealthTreatmentRetraction(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, treatment,
                    UUID.randomUUID(), null, fixture.actor());

            assertThat(repository.findHealthTreatmentRetractionByTreatment(
                    new TenantId(fixture.tenant()), fixture.farm(), animal, treatment))
                    .contains(created);
            connection.rollback();
        }
    }

    @Test
    void rejectsTreatmentOfAnotherAnimalInTheSameTenantAndFarmUnderRls() throws Exception {
        Fixture fixture = fixture();
        UUID ownAnimal = seedAnimal(fixture);
        UUID otherAnimal = seedAnimal(fixture);
        UUID otherTreatment = seedTreatment(fixture, otherAnimal, "VACCINATION", "BRUCELLOSIS",
                LocalDate.of(2026, 7, 22));

        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            JdbcHerdManagementRepository repository = repository(connection);

            assertThatThrownBy(() -> repository.insertHealthTreatmentRetraction(
                    new TenantId(fixture.tenant()), fixture.farm(), ownAnimal, otherTreatment,
                    UUID.randomUUID(), null, fixture.actor()))
                    .isInstanceOf(DataAccessException.class);
            connection.rollback();
        }
    }

    @Test
    void rejectsTreatmentOfAnotherFarmInTheSameTenantUnderRls() throws Exception {
        Fixture own = fixture();
        UUID ownAnimal = seedAnimal(own);
        UUID otherFarm = UUID.randomUUID();
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                otherFarm, own.tenant(), "Outra fazenda");
        Fixture other = new Fixture(own.tenant(), otherFarm, own.actor());
        UUID otherAnimal = seedAnimal(other);
        UUID otherTreatment = seedTreatment(other, otherAnimal, "VACCINATION", "BRUCELLOSIS",
                LocalDate.of(2026, 7, 22));

        try (Connection connection = apiConnection()) {
            setTenant(connection, own.tenant());
            JdbcHerdManagementRepository repository = repository(connection);

            assertThatThrownBy(() -> repository.insertHealthTreatmentRetraction(
                    new TenantId(own.tenant()), own.farm(), ownAnimal, otherTreatment,
                    UUID.randomUUID(), null, own.actor()))
                    .isInstanceOf(DataAccessException.class);
            connection.rollback();
        }
    }

    @Test
    void isolatesTenantsUnderRls() throws Exception {
        Fixture own = fixture();
        UUID animal = seedAnimal(own);
        UUID treatment = seedTreatment(own, animal, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 7, 22));
        UUID operation = UUID.randomUUID();
        Fixture other = fixture();

        try (Connection admin = adminConnection()) {
            repository(admin).insertHealthTreatmentRetraction(new TenantId(own.tenant()), own.farm(), animal,
                    treatment, operation, null, own.actor());
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, other.tenant());
            JdbcHerdManagementRepository repository = repository(connection);

            assertThat(repository.findHealthTreatmentRetractionByOperation(
                    new TenantId(other.tenant()), other.farm(), operation)).isEmpty();
            assertThatThrownBy(() -> repository.insertHealthTreatmentRetraction(
                    new TenantId(own.tenant()), own.farm(), animal, treatment, UUID.randomUUID(),
                    null, other.actor()))
                    .isInstanceOf(DataAccessException.class);
            connection.rollback();
        }
    }

    @Test
    void deniesUpdateAndDeleteForAppApi() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID treatment = seedTreatment(fixture, animal, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 7, 22));

        try (Connection admin = adminConnection()) {
            repository(admin).insertHealthTreatmentRetraction(new TenantId(fixture.tenant()), fixture.farm(),
                    animal, treatment, UUID.randomUUID(), null, fixture.actor());
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));

            assertThatThrownBy(() -> jdbc.update(
                    "update app.animal_health_treatment_retractions set reason='x' where tenant_id=:t",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("t", fixture.tenant())))
                    .isInstanceOf(DataAccessException.class);
            connection.rollback();
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));

            assertThatThrownBy(() -> jdbc.update(
                    "delete from app.animal_health_treatment_retractions where tenant_id=:t",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("t", fixture.tenant())))
                    .isInstanceOf(DataAccessException.class);
            connection.rollback();
        }
    }

    private Fixture fixture() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",
                tenant, "Organização");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                farm, tenant, "Fazenda");
        return new Fixture(tenant, farm, actor);
    }

    private UUID seedAnimal(Fixture fixture) throws Exception {
        UUID animal = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status)
                values(?,?,?,?,'FEMALE',?,'ACTIVE')
                """, animal, fixture.tenant(), fixture.farm(), "B-" + animal.toString().substring(0, 4),
                LocalDate.of(2026, 4, 22));
        return animal;
    }

    private UUID seedTreatment(
            Fixture fixture, UUID animal, String type, String procedure, LocalDate occurredOn
    ) throws Exception {
        UUID treatment = UUID.randomUUID();
        if (procedure == null) {
            executeAsAdmin("""
                    insert into app.animal_health_treatments
                      (id,tenant_id,farm_id,animal_id,operation_id,treatment_type,procedure_code,
                       occurred_on,actor_user_id)
                    values(?,?,?,?,?,?,null,?,?)
                    """, treatment, fixture.tenant(), fixture.farm(), animal, UUID.randomUUID(),
                    type, occurredOn, fixture.actor());
        } else {
            executeAsAdmin("""
                    insert into app.animal_health_treatments
                      (id,tenant_id,farm_id,animal_id,operation_id,treatment_type,procedure_code,
                       occurred_on,actor_user_id)
                    values(?,?,?,?,?,?,?,?,?)
                    """, treatment, fixture.tenant(), fixture.farm(), animal, UUID.randomUUID(),
                    type, procedure, occurredOn, fixture.actor());
        }
        return treatment;
    }

    private JdbcHerdManagementRepository repository(Connection connection) {
        return new JdbcHerdManagementRepository(
                new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    private record Fixture(UUID tenant, UUID farm, UUID actor) {
    }
}
