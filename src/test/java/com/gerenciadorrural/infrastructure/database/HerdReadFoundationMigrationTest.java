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

class HerdReadFoundationMigrationTest extends PostgresMigrationTestSupport {
    @Test
    void constraintsAndCompositeFarmForeignKeyProtectAnimals() throws Exception {
        UUID tenantA = organization();
        UUID tenantB = organization();
        UUID farmA = farm(tenantA);
        UUID farmA2 = farm(tenantA);
        UUID farmB = farm(tenantB);

        animal(UUID.randomUUID(), tenantA, farmA, " A-1 ", "Boi de corte", "FEMALE", "ACTIVE");
        animal(UUID.randomUUID(), tenantA, farmA, "A-2", null, "MALE", "ACTIVE");
        animal(UUID.randomUUID(), tenantA, farmA, "I".repeat(100), "N".repeat(255), "MALE", "ACTIVE");
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "\tA-1\n", null, "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmB, "A-3", null, "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "\t", null, "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "\n", null, "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "\r\n", null, "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "   ", null, "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, " ".repeat(100) + "A", null, "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "A-4", "\t\r\n", "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "A-5", " ".repeat(255) + "Nome", "MALE", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "A-6", null, "OTHER", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> animal(UUID.randomUUID(), tenantA, farmA, "A-7", null, "MALE", "INVALID"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,version) values(?,?,?,?,?,-1)", UUID.randomUUID(), tenantA, farmA, "A-8", "MALE"))
                .isInstanceOf(SQLException.class);
        animal(UUID.randomUUID(), tenantA, farmA2, "A-1", null, "MALE", "ACTIVE");
    }

    @Test
    void grantsAreRestrictedAndRlsIsolatesAnimalsByTenant() throws Exception {
        UUID tenantA = organization();
        UUID tenantB = organization();
        UUID farmA = farm(tenantA);
        UUID farmB = farm(tenantB);
        UUID animalA = UUID.randomUUID();
        UUID animalB = UUID.randomUUID();
        animal(animalA, tenantA, farmA, "A-1", null, "FEMALE", "ACTIVE");
        animal(animalB, tenantB, farmB, "B-1", null, "MALE", "ACTIVE");

        try (Connection connection = adminConnection();
             PreparedStatement privileges = connection.prepareStatement("""
                     select
                         has_table_privilege('app_api', 'app.animals', 'SELECT'),
                         has_table_privilege('app_api', 'app.animals', 'INSERT'),
                         has_table_privilege('app_api', 'app.animals', 'UPDATE'),
                         has_table_privilege('app_api', 'app.animals', 'DELETE'),
                         has_table_privilege('app_api', 'app.animals', 'TRUNCATE'),
                         has_table_privilege('app_api', 'app.animals', 'REFERENCES'),
                         has_table_privilege('app_api', 'app.animals', 'TRIGGER')
                     """);
             ResultSet result = privileges.executeQuery()) {
            result.next();
            assertThat(result.getBoolean(1)).isTrue();
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.getBoolean(3)).isFalse();
            assertThat(result.getBoolean(4)).isFalse();
            assertThat(result.getBoolean(5)).isFalse();
            assertThat(result.getBoolean(6)).isFalse();
            assertThat(result.getBoolean(7)).isFalse();
        }
        try (Connection connection = adminConnection();
             PreparedStatement grants = connection.prepareStatement("""
                     select count(*)
                     from information_schema.role_table_grants
                     where table_schema = 'app' and table_name = 'animals' and grantee = 'PUBLIC'
                     """);
             ResultSet result = grants.executeQuery()) {
            result.next();
            assertThat(result.getLong(1)).isZero();
        }
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select relrowsecurity, relforcerowsecurity from pg_class where oid = 'app.animals'::regclass")) {
            result.next();
            assertThat(result.getBoolean(1)).isTrue();
            assertThat(result.getBoolean(2)).isTrue();
        }

        try (Connection connection = apiConnection()) {
            assertThat(animalIds(connection)).isEmpty();
            connection.rollback();
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            assertThat(animalIds(connection)).containsExactly(animalA);
            connection.rollback();
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantB);
            assertThat(animalIds(connection)).containsExactly(animalB);
            connection.rollback();
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

    private void animal(UUID id, UUID tenant, UUID farm, String identification, String name, String sex, String status) throws SQLException {
        executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,name,sex,status) values(?,?,?,?,?,?,?)", id, tenant, farm, identification, name, sex, status);
    }

    private Set<UUID> animalIds(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select id from app.animals")) {
            Set<UUID> ids = new HashSet<>();
            while (result.next()) {
                ids.add(result.getObject(1, UUID.class));
            }
            return ids;
        }
    }
}
