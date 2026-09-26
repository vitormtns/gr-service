package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.Pending;
import com.gerenciadorrural.modules.herd.domain.PendingWorkType;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdManagementRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveHealthPendingRepositoryIntegrationTest extends PostgresMigrationTestSupport {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 22);
    private static final LocalDate OLDER = LocalDate.of(2026, 7, 1);
    private static final LocalDate NEWER = LocalDate.of(2026, 8, 1);
    private static final LocalDate OVERDUE = LocalDate.of(2026, 9, 1);
    private static final LocalDate FUTURE = LocalDate.of(2026, 10, 1);

    @Test
    void newestActiveVaccinationRemainsLatest() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        seedTreatment(fixture, animal, "VACCINATION", OLDER, OVERDUE);
        seedTreatment(fixture, animal, "VACCINATION", NEWER, OVERDUE);

        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            List<Pending> items = pending(repository(connection), fixture, animal, PendingWorkType.VACCINATION_DUE);
            assertThat(items).singleElement().satisfies(item -> {
                assertThat(item.lastPerformedOn()).isEqualTo(NEWER);
                assertThat(item.date()).isEqualTo(OVERDUE);
            });
        }
    }

    @Test
    void retractedNewestVaccinationFallsBackToOlderDueTreatment() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID older = seedTreatment(fixture, animal, "VACCINATION", OLDER, OVERDUE);
        UUID newer = seedTreatment(fixture, animal, "VACCINATION", NEWER, FUTURE);
        retract(fixture, animal, newer);

        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            JdbcHerdManagementRepository repository = repository(connection);
            assertThat(pending(repository, fixture, animal, PendingWorkType.VACCINATION_DUE))
                    .singleElement().satisfies(item -> {
                        assertThat(item.lastPerformedOn()).isEqualTo(OLDER);
                        assertThat(item.date()).isEqualTo(OVERDUE);
                    });
            assertThat(repository.treatments(new TenantId(fixture.tenant()), fixture.farm(), animal, 10, 0))
                    .extracting(t -> t.id()).containsExactly(newer, older);
        }
    }

    @Test
    void futureOlderVaccinationDoesNotBecomeDueAfterNewerOverdueDoseIsRetracted() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        seedTreatment(fixture, animal, "VACCINATION", OLDER, FUTURE);
        UUID newer = seedTreatment(fixture, animal, "VACCINATION", NEWER, OVERDUE);
        retract(fixture, animal, newer);

        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            assertThat(pending(repository(connection), fixture, animal, PendingWorkType.VACCINATION_DUE)).isEmpty();
        }
    }

    @Test
    void onlyRetractedVaccinationHasNoEffectiveLatestTreatment() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        UUID treatment = seedTreatment(fixture, animal, "VACCINATION", OLDER, OVERDUE);
        retract(fixture, animal, treatment);

        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            assertThat(pending(repository(connection), fixture, animal, PendingWorkType.VACCINATION_DUE)).isEmpty();
        }
    }

    @Test
    void retractedNewestDewormingFallsBackToOlderDueTreatment() throws Exception {
        Fixture fixture = fixture();
        UUID animal = seedAnimal(fixture);
        seedTreatment(fixture, animal, "DEWORMING", OLDER, OVERDUE);
        UUID newer = seedTreatment(fixture, animal, "DEWORMING", NEWER, FUTURE);
        retract(fixture, animal, newer);

        try (Connection connection = apiConnection()) {
            setTenant(connection, fixture.tenant());
            assertThat(pending(repository(connection), fixture, animal, PendingWorkType.DEWORMING_DUE))
                    .singleElement().satisfies(item -> {
                        assertThat(item.lastPerformedOn()).isEqualTo(OLDER);
                        assertThat(item.date()).isEqualTo(OVERDUE);
                    });
        }
    }

    @Test
    void retractionInAnotherTenantAndFarmDoesNotHideActiveTreatment() throws Exception {
        Fixture first = fixture();
        UUID firstAnimal = seedAnimal(first);
        UUID firstTreatment = seedTreatment(first, firstAnimal, "VACCINATION", OLDER, OVERDUE);
        retract(first, firstAnimal, firstTreatment);

        Fixture second = fixture();
        UUID secondAnimal = seedAnimal(second);
        seedTreatment(second, secondAnimal, "VACCINATION", OLDER, OVERDUE);

        try (Connection connection = apiConnection()) {
            setTenant(connection, second.tenant());
            assertThat(pending(repository(connection), second, secondAnimal, PendingWorkType.VACCINATION_DUE))
                    .singleElement().satisfies(item -> assertThat(item.lastPerformedOn()).isEqualTo(OLDER));
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, first.tenant());
            assertThat(pending(repository(connection), first, firstAnimal, PendingWorkType.VACCINATION_DUE))
                    .isEmpty();
        }
    }

    private List<Pending> pending(JdbcHerdManagementRepository repository, Fixture fixture, UUID animal,
                                  PendingWorkType type) {
        return repository.pending(new TenantId(fixture.tenant()), fixture.farm(), REFERENCE,
                90, 14, type, animal, 20, 0);
    }

    private Fixture fixture() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')",
                tenant, "Organização");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                farm, tenant, "Fazenda");
        return new Fixture(tenant, farm);
    }

    private UUID seedAnimal(Fixture fixture) throws Exception {
        UUID animal = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status)
                values(?,?,?,?,'FEMALE',?,'ACTIVE')
                """, animal, fixture.tenant(), fixture.farm(), "E-" + animal.toString().substring(0, 8),
                LocalDate.of(2025, 1, 1));
        return animal;
    }

    private UUID seedTreatment(Fixture fixture, UUID animal, String type, LocalDate occurred,
                               LocalDate nextDue) throws Exception {
        UUID treatment = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.animal_health_treatments
                  (id,tenant_id,farm_id,animal_id,operation_id,treatment_type,occurred_on,next_due_on)
                values(?,?,?,?,?,?,?,?)
                """, treatment, fixture.tenant(), fixture.farm(), animal, UUID.randomUUID(), type,
                occurred, nextDue);
        return treatment;
    }

    private void retract(Fixture fixture, UUID animal, UUID treatment) throws Exception {
        executeAsAdmin("""
                insert into app.animal_health_treatment_retractions
                  (id,tenant_id,farm_id,animal_id,treatment_id,operation_id)
                values(?,?,?,?,?,?)
                """, UUID.randomUUID(), fixture.tenant(), fixture.farm(), animal, treatment, UUID.randomUUID());
    }

    private JdbcHerdManagementRepository repository(Connection connection) {
        return new JdbcHerdManagementRepository(
                new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    private record Fixture(UUID tenant, UUID farm) {
    }
}
