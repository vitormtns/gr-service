package com.gerenciadorrural.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.ReproductionServiceType;
import com.gerenciadorrural.modules.herd.infrastructure.*;
import com.gerenciadorrural.shared.infrastructure.database.*;
import com.gerenciadorrural.shared.tenancy.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class HerdAnimalImportIntegrationTest extends PostgresMigrationTestSupport {
    private HikariDataSource dataSource;
    private SpringTenantTransactionExecutor transactions;
    private JdbcHerdAnimalImportRepository repository;
    private ImportCurrentFarmAnimals importer;
    private CorrectCurrentFarmAnimalMother motherCorrections;
    private BatchBreedCurrentFarmAnimals breedingBatches;
    private JdbcHerdBreedingBatchRepository breedingRepository;
    private RecordCurrentFarmAnimalNote notes;
    private TenantContext context;
    private UUID farmId;
    private UUID tenantId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                do $$ begin
                    if not exists(select 1 from pg_roles where rolname='herd_import_runtime') then
                        create role herd_import_runtime login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls password 'herd-import-test';
                    end if;
                    grant app_api to herd_import_runtime;
                end $$
                """);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername("herd_import_runtime");
        config.setPassword("herd-import-test");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        dataSource = new HikariDataSource(config);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        transactions = new SpringTenantTransactionExecutor(new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)), named,
                new TransactionalDatabaseRole(jdbc, new DatabaseAccessProperties("app", "app_api")));
        repository = new JdbcHerdAnimalImportRepository(named);
        var events = new JdbcAnimalEventRepository(named, new ObjectMapper().findAndRegisterModules());
        var create = new CreateCurrentFarmAnimal(transactions, new JdbcHerdAnimalWriteRepository(named),
                Clock.systemUTC(), events);
        importer = new ImportCurrentFarmAnimals(transactions, create, repository, Clock.systemUTC());
        motherCorrections = new CorrectCurrentFarmAnimalMother(transactions,
                new JdbcHerdAnimalProfileRepository(named), new JdbcMaternalRelationRepository(named), events);
        var reproduction = new ManageHerdReproduction(transactions,
                new JdbcHerdAnimalProfileRepository(named), new JdbcHerdReproductionRepository(named),
                new JdbcHerdAnimalWriteRepository(named), new JdbcMaternalRelationRepository(named),
                events, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
        breedingRepository = new JdbcHerdBreedingBatchRepository(named);
        breedingBatches = new BatchBreedCurrentFarmAnimals(transactions, reproduction, breedingRepository);
        notes = new RecordCurrentFarmAnimalNote(transactions, new JdbcHerdAnimalProfileRepository(named),
                events, Clock.systemUTC());
        UUID userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        farmId = UUID.randomUUID();
        executeAsAdmin("insert into app.users(id,status) values (?, 'ACTIVE')", userId);
        executeAsAdmin("insert into app.organizations(id,name,status) values (?, 'Import tenant', 'ACTIVE')", tenantId);
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values (?, ?, 'Import farm', 'ACTIVE')", farmId, tenantId);
        context = new TenantContext(new TenantId(tenantId), userId, farmId, UUID.randomUUID(), "OWNER", "ALL_FARMS");
    }

    @AfterEach
    void close() {
        if (dataSource != null) dataSource.close();
    }

    @Test
    void importsMotherAndChildAtomicallyAndReplaysWithoutDuplicates() throws Exception {
        UUID operation = UUID.randomUUID();
        UUID mother = UUID.randomUUID();
        UUID calf = UUID.randomUUID();
        var rows = List.of(row(calf, "Filho", HerdAnimalSex.MALE, LocalDate.of(2023, 1, 1), "Mãe"),
                row(mother, "Mãe", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), null));
        assertThat(importer.execute(context, operation, rows).replayed()).isFalse();
        assertThat(importer.execute(context, operation, rows).replayed()).isTrue();
        assertThat(count("app.animals")).isEqualTo(2);
        assertThat(count("app.animal_events")).isEqualTo(2);
        assertThat(count("app.animal_maternal_relations")).isEqualTo(1);
        assertThat(transactions.execute(context, () -> repository.motherId(context.tenantId(), calf))).contains(mother);
        assertThatThrownBy(() -> importer.execute(context, operation,
                List.of(row(calf, "Outro", HerdAnimalSex.MALE, LocalDate.of(2023, 1, 1), null))))
                .isInstanceOf(HerdAnimalIdempotencyConflictException.class);
    }

    @Test
    void invalidMotherRollsBackAnimalsEventsAndReceipt() throws Exception {
        assertThatThrownBy(() -> importer.execute(context, UUID.randomUUID(), List.of(
                row(UUID.randomUUID(), "Filho", HerdAnimalSex.MALE, null, "Desconhecida"))))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);
        assertThat(count("app.animals")).isZero();
        assertThat(count("app.animal_events")).isZero();
        assertThat(count("app.herd_animal_imports")).isZero();
    }

    @Test
    void receiptAndLookupRespectTenantAndFarmScope() throws Exception {
        UUID operation = UUID.randomUUID();
        UUID animal = UUID.randomUUID();
        importer.execute(context, operation, List.of(row(animal, "A-1", HerdAnimalSex.FEMALE, null, null)));
        UUID otherTenant = UUID.randomUUID();
        UUID otherFarm = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values (?, 'Other', 'ACTIVE')", otherTenant);
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values (?, ?, 'Other farm', 'ACTIVE')", otherFarm, otherTenant);
        TenantContext other = new TenantContext(new TenantId(otherTenant), context.userId(), otherFarm,
                UUID.randomUUID(), "OWNER", "ALL_FARMS");
        assertThat(transactions.execute(other, () -> repository.find(other.tenantId(), other.farmId(), operation))).isEmpty();
        assertThat(transactions.execute(other, () -> repository.findByIdentification(other.tenantId(), other.farmId(), "A-1"))).isEmpty();
        assertThatThrownBy(() -> transactions.execute(other,
                () -> repository.save(context.tenantId(), context.farmId(), UUID.randomUUID(),
                        new com.gerenciadorrural.modules.herd.domain.HerdAnimalImportRepository.Receipt(
                                "0".repeat(64), List.of(animal))))).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void linksCorrectsAndRemovesMotherWithVersionedAuditedReplay() throws Exception {
        UUID firstMother = UUID.randomUUID();
        UUID secondMother = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        importer.execute(context, UUID.randomUUID(), List.of(
                row(firstMother, "M-1", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), null),
                row(secondMother, "M-2", HerdAnimalSex.FEMALE, LocalDate.of(2020, 2, 1), null),
                row(child, "C-1", HerdAnimalSex.MALE, LocalDate.of(2024, 1, 1), null)));
        UUID operation = UUID.randomUUID();
        assertThat(motherCorrections.execute(context, child, operation, 0L, firstMother).version()).isOne();
        assertThat(motherCorrections.execute(context, child, operation, 0L, firstMother).replayed()).isTrue();
        assertThatThrownBy(() -> motherCorrections.execute(context, child, operation, 0L, secondMother))
                .isInstanceOf(HerdOperationIdempotencyConflictException.class);
        assertThatThrownBy(() -> motherCorrections.execute(context, child, UUID.randomUUID(), 0L, secondMother))
                .isInstanceOf(HerdAnimalVersionConflictException.class);
        assertThat(motherCorrections.execute(context, child, UUID.randomUUID(), 1L, secondMother).version())
                .isEqualTo(2);
        assertThat(transactions.execute(context, () -> repository.motherId(context.tenantId(), child)))
                .contains(secondMother);
        assertThat(motherCorrections.execute(context, child, UUID.randomUUID(), 2L, null).version())
                .isEqualTo(3);
        assertThat(transactions.execute(context, () -> repository.motherId(context.tenantId(), child)))
                .isEmpty();
        assertThat(count("app.animal_events")).isEqualTo(6);
    }

    @Test
    void rejectsCyclesAndCrossFarmMothersWithoutPartialWrites() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        importer.execute(context, UUID.randomUUID(), List.of(
                row(a, "A", HerdAnimalSex.FEMALE, null, null),
                row(b, "B", HerdAnimalSex.FEMALE, null, null)));
        motherCorrections.execute(context, b, UUID.randomUUID(), 0L, a);
        assertThatThrownBy(() -> motherCorrections.execute(context, a, UUID.randomUUID(), 0L, b))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);
        UUID otherFarm = UUID.randomUUID();
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values (?, ?, 'Other farm', 'ACTIVE')",
                otherFarm, tenantId);
        UUID outsider = UUID.randomUUID();
        executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex) values (?,?,?,'OUT','FEMALE')",
                outsider, tenantId, otherFarm);
        assertThatThrownBy(() -> motherCorrections.execute(context, b, UUID.randomUUID(), 1L, outsider))
                .isInstanceOf(HerdAnimalNotFoundException.class);
        assertThat(count("app.animal_events")).isEqualTo(3);
    }

    @Test
    void refusesToRewriteMaternalEvidenceCreatedByCalving() throws Exception {
        UUID mother = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        importer.execute(context, UUID.randomUUID(), List.of(
                row(mother, "M-1", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), null),
                row(child, "C-1", HerdAnimalSex.MALE, LocalDate.of(2024, 1, 1), null)));
        UUID pregnancy = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.animal_pregnancies
                  (id,tenant_id,farm_id,mother_animal_id,service_type,service_on,
                   expected_calving_on,status,ended_on,calf_animal_id,operation_id,command_payload)
                values (?,?,?,?,'INSEMINATION','2023-04-01','2024-01-01','CALVED','2024-01-01',?,?,'{}')
                """, pregnancy, tenantId, farmId, mother, child, UUID.randomUUID());
        executeAsAdmin("""
                insert into app.animal_maternal_relations
                  (tenant_id,mother_animal_id,calf_animal_id,pregnancy_id) values (?,?,?,?)
                """, tenantId, mother, child, pregnancy);
        assertThatThrownBy(() -> motherCorrections.execute(context, child, UUID.randomUUID(), 0L, null))
                .isInstanceOf(HerdMotherCorrectionConflictException.class);
        assertThat(transactions.execute(context, () -> repository.motherId(context.tenantId(), child)))
                .contains(mother);
        assertThat(count("app.animal_events")).isEqualTo(2);
    }

    @Test
    void breedsMultipleMothersAtomicallyAndReplaysReceipt() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        importer.execute(context, UUID.randomUUID(), List.of(
                row(first, "M-1", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), null),
                row(second, "M-2", HerdAnimalSex.FEMALE, LocalDate.of(2021, 1, 1), null)));
        UUID operation = UUID.randomUUID();
        var mothers = List.of(new BatchBreedCurrentFarmAnimals.Mother(first, 0L),
                new BatchBreedCurrentFarmAnimals.Mother(second, 0L));
        var result = breedingBatches.execute(context, operation, ReproductionServiceType.INSEMINATION,
                LocalDate.of(2026, 1, 1), "Sêmen A", null, null, mothers);
        assertThat(result.items()).hasSize(2);
        assertThat(breedingBatches.execute(context, operation, ReproductionServiceType.INSEMINATION,
                LocalDate.of(2026, 1, 1), "Sêmen A", null, null, mothers))
                .isEqualTo(new BatchBreedCurrentFarmAnimals.Result(result.items(), true));
        assertThat(count("app.animal_pregnancies")).isEqualTo(2);
        try (Connection connection = adminConnection();
             var statement = connection.createStatement();
             ResultSet dates = statement.executeQuery(
                     "select expected_calving_on from app.animal_pregnancies order by mother_animal_id")) {
            assertThat(dates.next()).isTrue();
            assertThat(dates.getDate(1).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 11));
            assertThat(dates.next()).isTrue();
            assertThat(dates.getDate(1).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 11));
            assertThat(dates.next()).isFalse();
        }
        assertThat(count("app.herd_breeding_batches")).isOne();
        assertThatThrownBy(() -> breedingBatches.execute(context, UUID.randomUUID(),
                ReproductionServiceType.INSEMINATION, LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 10, 12), null, mothers))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);
        assertThat(count("app.herd_breeding_batches")).isOne();
        UUID otherTenant = UUID.randomUUID();
        UUID otherFarm = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values (?, 'Other', 'ACTIVE')", otherTenant);
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values (?, ?, 'Other farm', 'ACTIVE')",
                otherFarm, otherTenant);
        TenantContext other = new TenantContext(new TenantId(otherTenant), context.userId(), otherFarm,
                UUID.randomUUID(), "OWNER", "ALL_FARMS");
        assertThat(transactions.execute(other,
                () -> breedingRepository.find(other.tenantId(), other.farmId(), operation))).isEmpty();
        assertThatThrownBy(() -> breedingBatches.execute(context, operation,
                ReproductionServiceType.INSEMINATION, LocalDate.of(2026, 1, 1), "Outro sêmen",
                null, null, mothers)).isInstanceOf(HerdOperationIdempotencyConflictException.class);
    }

    @Test
    void failedBreedingBatchRollsBackEarlierMothersAndReceipt() throws Exception {
        UUID female = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID male = UUID.fromString("00000000-0000-0000-0000-000000000002");
        importer.execute(context, UUID.randomUUID(), List.of(
                row(female, "F", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), null),
                row(male, "M", HerdAnimalSex.MALE, LocalDate.of(2020, 1, 1), null)));
        assertThatThrownBy(() -> breedingBatches.execute(context, UUID.randomUUID(),
                ReproductionServiceType.INSEMINATION, LocalDate.of(2026, 1, 1), null,
                null, null, List.of(new BatchBreedCurrentFarmAnimals.Mother(female, 0L),
                        new BatchBreedCurrentFarmAnimals.Mother(male, 0L))))
                .isInstanceOf(HerdLifecycleConflictException.class);
        assertThat(count("app.animal_pregnancies")).isZero();
        assertThat(count("app.herd_breeding_batches")).isZero();
        assertThat(count("app.animal_events")).isEqualTo(2);
    }

    @Test
    void recordsFreeAnimalNoteWithHistoryVersionAndIdempotentReplay() throws Exception {
        UUID animal = UUID.randomUUID();
        importer.execute(context, UUID.randomUUID(), List.of(
                row(animal, "A-1", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), null)));
        UUID operation = UUID.randomUUID();
        LocalDate occurred = LocalDate.of(2026, 1, 1);
        var recorded = notes.execute(context, animal, operation, 0L, occurred, "  Observação do manejo  ");
        assertThat(recorded.notes()).isEqualTo("Observação do manejo");
        assertThat(recorded.version()).isOne();
        assertThat(notes.execute(context, animal, operation, 0L, occurred, "Observação do manejo").replayed())
                .isTrue();
        assertThatThrownBy(() -> notes.execute(context, animal, operation, 0L, occurred, "Outra nota"))
                .isInstanceOf(HerdOperationIdempotencyConflictException.class);
        assertThatThrownBy(() -> notes.execute(context, animal, UUID.randomUUID(), 0L, occurred, "Outra nota"))
                .isInstanceOf(HerdAnimalVersionConflictException.class);
        assertThat(count("app.animal_events")).isEqualTo(2);
    }

    private static ImportCurrentFarmAnimals.Row row(UUID id, String identification, HerdAnimalSex sex,
                                                    LocalDate birth, String mother) {
        return new ImportCurrentFarmAnimals.Row(id, identification, null, sex, null, birth, mother);
    }

    private long count(String table) throws Exception {
        try (Connection connection = adminConnection(); var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select count(*) from " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
