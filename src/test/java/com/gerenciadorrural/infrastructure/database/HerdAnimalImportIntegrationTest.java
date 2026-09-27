package com.gerenciadorrural.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
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
        var create = new CreateCurrentFarmAnimal(transactions, new JdbcHerdAnimalWriteRepository(named),
                Clock.systemUTC(), new JdbcAnimalEventRepository(named, new ObjectMapper().findAndRegisterModules()));
        importer = new ImportCurrentFarmAnimals(transactions, create, repository, Clock.systemUTC());
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
