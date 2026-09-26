package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.BrucellosisPendingCandidate;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdManagementRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrucellosisCandidatesRepositoryIntegrationTest extends PostgresMigrationTestSupport {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 22);
    private static final LocalDate BIRTH = LocalDate.of(2026, 4, 22);

    @Test
    void returnsClassifiableFemalesWithStructuredHistoryOnly() throws Exception {
        Fixture fixture = fixture();
        UUID noHistory = seedAnimal(fixture, "FEMALE", BIRTH, "ACTIVE");
        UUID structured = seedAnimal(fixture, "FEMALE", BIRTH, "ACTIVE");
        seedTreatment(fixture, structured, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 7, 22));
        seedTreatment(fixture, structured, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 8, 22));
        UUID legacy = seedAnimal(fixture, "FEMALE", BIRTH, "ACTIVE");
        seedTreatment(fixture, legacy, "VACCINATION", null, LocalDate.of(2026, 7, 22));
        UUID future = seedAnimal(fixture, "FEMALE", BIRTH, "ACTIVE");
        seedTreatment(fixture, future, "VACCINATION", "BRUCELLOSIS", LocalDate.of(2026, 12, 1));
        seedAnimal(fixture, "MALE", BIRTH, "ACTIVE");
        seedAnimal(fixture, "FEMALE", null, "ACTIVE");
        seedAnimal(fixture, "FEMALE", BIRTH, "SOLD");
        UUID otherFarm = UUID.randomUUID();
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                otherFarm, fixture.tenant(), "Outra");
        executeAsAdmin("""
                insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status)
                values(?,?,?,?,'FEMALE',?,'ACTIVE')
                """, UUID.randomUUID(), fixture.tenant(), otherFarm, "OUTRA", BIRTH);

        try (Connection connection = adminConnection()) {
            JdbcHerdManagementRepository repository = repository(connection);
            List<BrucellosisPendingCandidate> candidates = repository.brucellosisPendingCandidates(
                    new TenantId(fixture.tenant()), fixture.farm(), REFERENCE, null);

            assertThat(candidates).extracting(BrucellosisPendingCandidate::animalId)
                    .containsExactlyInAnyOrder(noHistory, structured, legacy, future);
            assertThat(candidates).allSatisfy(candidate -> {
                assertThat(candidate.farmId()).isEqualTo(fixture.farm());
                assertThat(candidate.birthDate()).isEqualTo(BIRTH);
            });
            assertThat(byId(candidates, noHistory).treatments()).isEmpty();
            assertThat(byId(candidates, structured).treatments())
                    .extracting(treatment -> treatment.occurredOn())
                    .containsExactly(LocalDate.of(2026, 7, 22), LocalDate.of(2026, 8, 22));
            assertThat(byId(candidates, structured).treatments())
                    .allSatisfy(treatment -> assertThat(treatment.procedureCode()).hasToString("BRUCELLOSIS"));
            assertThat(byId(candidates, legacy).treatments()).isEmpty();
            assertThat(byId(candidates, future).treatments()).isEmpty();
        }
    }

    @Test
    void respectsAnimalIdFilter() throws Exception {
        Fixture fixture = fixture();
        seedAnimal(fixture, "FEMALE", BIRTH, "ACTIVE");
        UUID wanted = seedAnimal(fixture, "FEMALE", BIRTH, "ACTIVE");

        try (Connection connection = adminConnection()) {
            JdbcHerdManagementRepository repository = repository(connection);
            List<BrucellosisPendingCandidate> candidates = repository.brucellosisPendingCandidates(
                    new TenantId(fixture.tenant()), fixture.farm(), REFERENCE, wanted);

            assertThat(candidates).extracting(BrucellosisPendingCandidate::animalId)
                    .containsExactly(wanted);
        }
    }

    private BrucellosisPendingCandidate byId(List<BrucellosisPendingCandidate> candidates, UUID id) {
        return candidates.stream().filter(candidate -> candidate.animalId().equals(id)).findFirst()
                .orElseThrow();
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

    private UUID seedAnimal(Fixture fixture, String sex, LocalDate birth, String status) throws Exception {
        UUID animal = UUID.randomUUID();
        if (birth == null) {
            executeAsAdmin("""
                    insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status)
                    values(?,?,?,?,?,null,?)
                    """, animal, fixture.tenant(), fixture.farm(), "B-" + animal.toString().substring(0, 4),
                    sex, status);
        } else {
            executeAsAdmin("""
                    insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status)
                    values(?,?,?,?,?,?,?)
                    """, animal, fixture.tenant(), fixture.farm(), "B-" + animal.toString().substring(0, 4),
                    sex, birth, status);
        }
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
                    type, occurredOn, UUID.randomUUID());
        } else {
            executeAsAdmin("""
                    insert into app.animal_health_treatments
                      (id,tenant_id,farm_id,animal_id,operation_id,treatment_type,procedure_code,
                       occurred_on,actor_user_id)
                    values(?,?,?,?,?,?,?,?,?)
                    """, treatment, fixture.tenant(), fixture.farm(), animal, UUID.randomUUID(),
                    type, procedure, occurredOn, UUID.randomUUID());
        }
        return treatment;
    }

    private JdbcHerdManagementRepository repository(Connection connection) {
        return new JdbcHerdManagementRepository(
                new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    private record Fixture(UUID tenant, UUID farm) {
    }
}
