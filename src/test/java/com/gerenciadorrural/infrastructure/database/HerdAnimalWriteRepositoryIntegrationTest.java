package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.domain.NewHerdAnimal;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalWriteRepository;
import com.gerenciadorrural.shared.infrastructure.database.DatabaseAccessProperties;
import com.gerenciadorrural.shared.infrastructure.database.SpringTenantTransactionExecutor;
import com.gerenciadorrural.shared.infrastructure.database.TransactionalDatabaseRole;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HerdAnimalWriteRepositoryIntegrationTest extends PostgresMigrationTestSupport {

    private HikariDataSource dataSource;
    private SpringTenantTransactionExecutor transactions;
    private JdbcHerdAnimalWriteRepository repository;
    private UUID userA;
    private UUID userB;
    private UUID tenantA;
    private UUID tenantB;
    private UUID farmA;
    private UUID farmA2;
    private UUID farmB;

    @BeforeEach
    void setUp() throws Exception {
        createRuntimeRole();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername("herd_write_runtime");
        config.setPassword("herd-write-test");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        dataSource = new HikariDataSource(config);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        transactions = new SpringTenantTransactionExecutor(
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new NamedParameterJdbcTemplate(dataSource),
                new TransactionalDatabaseRole(jdbc, new DatabaseAccessProperties("app", "app_api"))
        );
        repository = new JdbcHerdAnimalWriteRepository(new NamedParameterJdbcTemplate(dataSource));
        createFixtures();
    }

    @AfterEach
    void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void insertsClientGeneratedIdAndLetsTheDatabaseApplyTechnicalDefaults() {
        UUID id = UUID.randomUUID();
        HerdAnimalSummary created = insert(contextA(), animal(id, tenantA, farmA, "A-001", "Brisa", LocalDate.of(2020, 2, 3)));

        assertThat(created.id()).isEqualTo(id);
        assertThat(created.identification()).isEqualTo("A-001");
        assertThat(created.name()).isEqualTo("Brisa");
        assertThat(created.sex()).isEqualTo(HerdAnimalSex.FEMALE);
        assertThat(created.birthDate()).isEqualTo(LocalDate.of(2020, 2, 3));
        assertThat(created.status().name()).isEqualTo("ACTIVE");
        assertThat(created.version()).isZero();
        assertThat(transactions.execute(contextA(), () -> repository.findById(new TenantId(tenantA), farmA, id)))
                .contains(created);

        StoredAnimal stored = stored(id);
        assertThat(stored.tenantId()).isEqualTo(tenantA);
        assertThat(stored.farmId()).isEqualTo(farmA);
        assertThat(stored.identification()).isEqualTo("A-001");
        assertThat(stored.name()).isEqualTo("Brisa");
        assertThat(stored.sex()).isEqualTo("FEMALE");
        assertThat(stored.birthDate()).isEqualTo(LocalDate.of(2020, 2, 3));
        assertThat(stored.status()).isEqualTo("ACTIVE");
        assertThat(stored.version()).isZero();
        assertThat(stored.createdAt()).isNotNull();
        assertThat(stored.updatedAt()).isNotNull();
    }

    @Test
    void acceptsNullNameAndBirthDateAndDoesNotGenerateAnotherId() {
        UUID id = UUID.randomUUID();
        HerdAnimalSummary created = insert(contextA(), animal(id, tenantA, farmA, "A-002", null, null));

        assertThat(created.id()).isEqualTo(id);
        assertThat(created.name()).isNull();
        assertThat(created.birthDate()).isNull();
        assertThat(stored(id).id()).isEqualTo(id);
    }

    @Test
    void rlsRejectsRepositoryInsertWhoseExplicitTenantDiffersFromTheTransactionContext() {
        assertThatThrownBy(() -> insert(contextA(), animal(UUID.randomUUID(), tenantB, farmB, "B-001", null, null)))
                .isInstanceOf(DataAccessException.class);
        assertThat(storedCount()).isZero();
        assertThat(transactions.execute(contextA(), () -> repository.findById(new TenantId(tenantB), farmB, UUID.randomUUID())))
                .isEmpty();
    }

    @Test
    void preservesPrimaryKeyAndNormalizedIdentificationConflictsForFutureClassification() {
        UUID id = UUID.randomUUID();
        insert(contextA(), animal(id, tenantA, farmA, "A-001", null, null));
        assertConstraint(() -> insert(contextA(), animal(id, tenantA, farmA, "Outro", null, null)), "animals_pkey");
        assertConstraint(() -> insert(contextA(), animal(UUID.randomUUID(), tenantA, farmA, "\tA-001\n", null, null)),
                "animals_tenant_farm_identification_unique");
    }

    @Test
    void allowsTheSameIdentificationInAnotherFarmOrTenant() {
        insert(contextA(), animal(UUID.randomUUID(), tenantA, farmA, "A-001", null, null));
        insert(contextA2(), animal(UUID.randomUUID(), tenantA, farmA2, "A-001", null, null));
        insert(contextB(), animal(UUID.randomUUID(), tenantB, farmB, "A-001", null, null));
        assertThat(storedCount()).isEqualTo(3);
    }

    private HerdAnimalSummary insert(TenantContext context, NewHerdAnimal animal) {
        return transactions.execute(context, () -> repository.insert(animal));
    }

    private NewHerdAnimal animal(UUID id, UUID tenant, UUID farm, String identification, String name, LocalDate birthDate) {
        return new NewHerdAnimal(id, new TenantId(tenant), farm, identification, name, HerdAnimalSex.FEMALE, birthDate);
    }

    private void assertConstraint(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String expectedConstraint) {
        assertThatThrownBy(action).isInstanceOf(DataIntegrityViolationException.class).satisfies(exception -> {
            PSQLException sqlException = postgresException(exception);
            assertThat(sqlException.getSQLState()).isEqualTo("23505");
            assertThat(sqlException.getServerErrorMessage()).isNotNull();
            assertThat(sqlException.getServerErrorMessage().getConstraint()).isEqualTo(expectedConstraint);
        });
    }

    private PSQLException postgresException(Throwable exception) {
        Throwable current = exception;
        while (current != null && !(current instanceof PSQLException)) {
            current = current.getCause();
        }
        if (current instanceof PSQLException postgresException) {
            return postgresException;
        }
        throw new AssertionError("A exceção PostgreSQL era esperada", exception);
    }

    private void createRuntimeRole() throws Exception {
        try (Connection connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    do $$ begin
                        if not exists (select 1 from pg_roles where rolname = 'herd_write_runtime') then
                            create role herd_write_runtime login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls password 'herd-write-test';
                        end if;
                        grant app_api to herd_write_runtime;
                    end $$
                    """);
        }
    }

    private void createFixtures() throws Exception {
        userA = UUID.randomUUID(); userB = UUID.randomUUID();
        tenantA = UUID.randomUUID(); tenantB = UUID.randomUUID();
        farmA = UUID.randomUUID(); farmA2 = UUID.randomUUID(); farmB = UUID.randomUUID();
        executeAsAdmin("insert into app.users(id,status) values (?, 'ACTIVE'), (?, 'ACTIVE')", userA, userB);
        executeAsAdmin("insert into app.organizations(id,name,status) values (?,?,'ACTIVE'), (?,?,'ACTIVE')", tenantA, "Tenant A", tenantB, "Tenant B");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values (?,?,?,'ACTIVE'), (?,?,?,'ACTIVE'), (?,?,?,'ACTIVE')",
                farmA, tenantA, "A1", farmA2, tenantA, "A2", farmB, tenantB, "B1");
    }

    private StoredAnimal stored(UUID id) {
        try (Connection connection = adminConnection(); var statement = connection.prepareStatement("""
                select id, tenant_id, farm_id, identification, name, sex, birth_date, status, version, created_at, updated_at
                from app.animals where id = ?
                """)) {
            statement.setObject(1, id);
            ResultSet result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            return new StoredAnimal(result.getObject("id", UUID.class), result.getObject("tenant_id", UUID.class),
                    result.getObject("farm_id", UUID.class), result.getString("identification"), result.getString("name"),
                    result.getString("sex"), result.getObject("birth_date", LocalDate.class), result.getString("status"),
                    result.getLong("version"), result.getObject("created_at", OffsetDateTime.class), result.getObject("updated_at", OffsetDateTime.class));
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private long storedCount() {
        try (Connection connection = adminConnection(); var statement = connection.createStatement(); ResultSet result = statement.executeQuery("select count(*) from app.animals")) {
            result.next();
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private TenantContext contextA() { return context(tenantA, userA, farmA); }
    private TenantContext contextA2() { return context(tenantA, userA, farmA2); }
    private TenantContext contextB() { return context(tenantB, userB, farmB); }
    private static TenantContext context(UUID tenant, UUID user, UUID farm) {
        return new TenantContext(new TenantId(tenant), user, farm, UUID.randomUUID(), "OWNER", "ALL_FARMS");
    }

    private record StoredAnimal(UUID id, UUID tenantId, UUID farmId, String identification, String name, String sex,
                                LocalDate birthDate, String status, long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
}
