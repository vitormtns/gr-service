package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HerdAnimalWritePersistenceMigrationTest extends PostgresMigrationTestSupport {

    @Test
    void grantsOnlyInsertAndPreservesSelectIsolation() throws Exception {
        UUID tenantA = organization();
        UUID tenantB = organization();
        UUID farmA = farm(tenantA);
        UUID farmB = farm(tenantB);
        UUID animalA = UUID.randomUUID();
        UUID animalB = UUID.randomUUID();
        animal(animalA, tenantA, farmA, "A-001");
        animal(animalB, tenantB, farmB, "B-001");

        try (Connection connection = adminConnection(); PreparedStatement privileges = connection.prepareStatement("""
                select
                    has_table_privilege('app_api', 'app.animals', 'SELECT'),
                    has_table_privilege('app_api', 'app.animals', 'INSERT'),
                    has_table_privilege('app_api', 'app.animals', 'UPDATE'),
                    has_table_privilege('app_api', 'app.animals', 'DELETE'),
                    has_table_privilege('app_api', 'app.animals', 'TRUNCATE'),
                    has_table_privilege('app_api', 'app.animals', 'REFERENCES'),
                    has_table_privilege('app_api', 'app.animals', 'TRIGGER')
                """); ResultSet result = privileges.executeQuery()) {
            result.next();
            assertThat(result.getBoolean(1)).isTrue();
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.getBoolean(3)).isFalse();
            assertThat(result.getBoolean(4)).isFalse();
            assertThat(result.getBoolean(5)).isFalse();
            assertThat(result.getBoolean(6)).isFalse();
            assertThat(result.getBoolean(7)).isFalse();
        }
        try (Connection connection = adminConnection(); PreparedStatement grants = connection.prepareStatement("""
                select count(*)
                from information_schema.role_table_grants
                where table_schema = 'app' and table_name = 'animals' and grantee = 'PUBLIC'
                """); ResultSet result = grants.executeQuery()) {
            result.next();
            assertThat(result.getLong(1)).isZero();
        }

        try (Connection connection = apiConnection()) {
            assertThat(animalIds(connection)).isEmpty();
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            assertThat(animalIds(connection)).containsExactly(animalA);
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantB);
            assertThat(animalIds(connection)).containsExactly(animalB);
        }
    }

    @Test
    void insertPolicyRequiresTheCurrentTenantAndCompositeFarmForeignKeyRemainsEffective() throws Exception {
        UUID tenantA = organization();
        UUID tenantB = organization();
        UUID farmA = farm(tenantA);
        UUID farmB = farm(tenantB);

        try (Connection connection = apiConnection()) {
            assertThatThrownBy(() -> animal(connection, UUID.randomUUID(), tenantA, farmA, "Sem tenant"))
                    .isInstanceOf(SQLException.class);
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            UUID animalA = UUID.randomUUID();
            animal(connection, animalA, tenantA, farmA, "A-002");
            assertThat(animalIds(connection)).containsExactly(animalA);
            assertThatThrownBy(() -> animal(connection, UUID.randomUUID(), tenantB, farmB, "B-negado"))
                    .isInstanceOf(SQLException.class);
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            assertThatThrownBy(() -> animal(connection, UUID.randomUUID(), tenantA, farmB, "FK-negada"))
                    .isInstanceOf(SQLException.class);
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantB);
            UUID animalB = UUID.randomUUID();
            animal(connection, animalB, tenantB, farmB, "B-002");
            assertThat(animalIds(connection)).containsExactly(animalB);
        }
    }

    private UUID organization() throws SQLException {
        UUID id = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')", id, "Org");
        return id;
    }

    private UUID farm(UUID tenant) throws SQLException {
        UUID id = UUID.randomUUID();
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')", id, tenant, "Farm");
        return id;
    }

    private void animal(UUID id, UUID tenant, UUID farm, String identification) throws SQLException {
        executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex) values(?,?,?,?,?)", id, tenant, farm, identification, "MALE");
    }

    private void animal(Connection connection, UUID id, UUID tenant, UUID farm, String identification) throws SQLException {
        execute(connection, "insert into app.animals(id,tenant_id,farm_id,identification,sex) values(?,?,?,?,?)", id, tenant, farm, identification, "MALE");
    }

    private Set<UUID> animalIds(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select id from app.animals")) {
            Set<UUID> ids = new HashSet<>();
            while (result.next()) {
                ids.add(result.getObject(1, UUID.class));
            }
            return ids;
        }
    }
}
