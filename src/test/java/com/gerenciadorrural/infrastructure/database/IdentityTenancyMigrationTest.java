package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

class IdentityTenancyMigrationTest extends PostgresMigrationTestSupport {

    @Test
    void shouldCreateThePrivateApplicationSchemaAndExpectedTables() throws SQLException {
        assertThat(queryBoolean("select exists (select 1 from pg_namespace where nspname = 'app')"))
                .isTrue();

        assertThat(queryStrings("""
                select table_name
                from information_schema.tables
                where table_schema = 'app'
                order by table_name
                """))
                .containsExactlyInAnyOrder(
                        "users",
                        "organizations",
                        "farms",
                        "animals",
                        "organization_memberships",
                        "membership_farm_scopes"
                );
    }

    @Test
    void shouldCreateRestrictedApiRole() throws SQLException {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolbypassrls
                     from pg_roles
                     where rolname = 'app_api'
                     """);
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean("rolcanlogin")).isFalse();
            assertThat(result.getBoolean("rolsuper")).isFalse();
            assertThat(result.getBoolean("rolcreatedb")).isFalse();
            assertThat(result.getBoolean("rolcreaterole")).isFalse();
            assertThat(result.getBoolean("rolbypassrls")).isFalse();
        }
    }

    @Test
    void shouldEnableAndForceRlsOnEveryTenantScopedTable() throws SQLException {
        assertThat(queryStrings("""
                select c.relname
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'app'
                  and c.relrowsecurity
                  and c.relforcerowsecurity
                """))
                .containsExactlyInAnyOrder(
                        "organizations",
                        "farms",
                        "animals",
                        "organization_memberships",
                        "membership_farm_scopes"
                );
    }

    @Test
    void shouldKeepBusinessTablesOutOfPublicAndPostgrestConfiguration() throws SQLException, IOException {
        assertThat(queryLong("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'users',
                    'organizations',
                    'farms',
                    'animals',
                    'organization_memberships',
                    'membership_farm_scopes'
                  )
                """))
                .isZero();

        String exposedSchemas = Files.readAllLines(
                        Path.of("supabase", "config.toml"), StandardCharsets.UTF_8)
                .stream()
                .map(String::trim)
                .filter(line -> line.startsWith("schemas ="))
                .findFirst()
                .orElseThrow();
        assertThat(exposedSchemas).doesNotContain("\"app\"");
    }

    @Test
    void shouldNotGrantApplicationTablesToPublic() throws SQLException {
        assertThat(queryLong("""
                select count(*)
                from information_schema.role_table_grants
                where table_schema = 'app'
                  and grantee = 'PUBLIC'
                """))
                .isZero();
    }

    @Test
    void shouldCreateUserWithExternallyGeneratedUuid() throws SQLException {
        UUID userId = UUID.randomUUID();

        executeAsAdmin("""
                insert into app.users (id, email, display_name, status)
                values (?, ?, ?, 'ACTIVE')
                """, userId, "pessoa@example.test", "Pessoa de teste");

        assertThat(queryUuid("select id from app.users where id = ?", userId)).isEqualTo(userId);
        assertThat(queryLong("select version from app.users where id = ?", userId)).isZero();
    }

    @Test
    void shouldNotHavePasswordTokenOrSupabaseKeyColumnsOnUsers() throws SQLException {
        assertThat(queryStrings("""
                select column_name
                from information_schema.columns
                where table_schema = 'app'
                  and table_name = 'users'
                """))
                .doesNotContain("password", "password_hash", "token", "access_token", "service_role_key");
    }

    @Test
    void shouldRejectInvalidUserStatus() {
        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.users (id, status)
                values (?, 'PENDING')
                """, UUID.randomUUID()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("users_status_check");
    }

    @Test
    void shouldCreateValidOrganization() throws SQLException {
        UUID tenantId = insertOrganization("Organização A");

        assertThat(queryUuid("select id from app.organizations where id = ?", tenantId))
                .isEqualTo(tenantId);
        assertThat(queryLong("select version from app.organizations where id = ?", tenantId))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldRejectBlankOrganizationName(String name) {
        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.organizations (id, name, status)
                values (?, ?, 'ACTIVE')
                """, UUID.randomUUID(), name))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("organizations_name_not_blank_check");
    }

    @Test
    void shouldRejectFarmWithoutOrganization() {
        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.farms (id, tenant_id, name, status)
                values (?, ?, 'Fazenda sem organização', 'ACTIVE')
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("farms_tenant_fk");
    }

    @Test
    void shouldRejectInvalidFarmStatus() throws SQLException {
        UUID tenantId = insertOrganization("Organização A");

        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.farms (id, tenant_id, name, status)
                values (?, ?, 'Fazenda A', 'BLOCKED')
                """, UUID.randomUUID(), tenantId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("farms_status_check");
    }

    @Test
    void shouldRejectDuplicateMembershipForSameUserAndTenant() throws SQLException {
        UUID userId = insertUser();
        UUID tenantId = insertOrganization("Organização A");
        insertMembership(tenantId, userId, "OWNER", "ALL_FARMS");

        assertThatThrownBy(() -> insertMembership(tenantId, userId, "VIEWER", "SELECTED_FARMS"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("organization_memberships_tenant_user_unique");
    }

    @Test
    void shouldAllowTheSameUserInDifferentTenants() throws SQLException {
        UUID userId = insertUser();
        UUID tenantA = insertOrganization("Organização A");
        UUID tenantB = insertOrganization("Organização B");

        insertMembership(tenantA, userId, "OWNER", "ALL_FARMS");
        insertMembership(tenantB, userId, "VIEWER", "SELECTED_FARMS");

        assertThat(queryLong(
                "select count(*) from app.organization_memberships where user_id = ?", userId))
                .isEqualTo(2);
    }

    @Test
    void shouldRejectInvalidMembershipRole() throws SQLException {
        UUID userId = insertUser();
        UUID tenantId = insertOrganization("Organização A");

        assertThatThrownBy(() -> insertMembership(tenantId, userId, "SUPER_ADMIN", "ALL_FARMS"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("organization_memberships_role_key_check");
    }

    @Test
    void shouldRejectInvalidFarmScopeMode() throws SQLException {
        UUID userId = insertUser();
        UUID tenantId = insertOrganization("Organização A");

        assertThatThrownBy(() -> insertMembership(tenantId, userId, "MANAGER", "CUSTOM"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("organization_memberships_farm_scope_mode_check");
    }

    @Test
    void shouldLinkMembershipAndFarmFromSameTenant() throws SQLException {
        UUID userId = insertUser();
        UUID tenantId = insertOrganization("Organização A");
        UUID membershipId = insertMembership(tenantId, userId, "OPERATOR", "SELECTED_FARMS");
        UUID farmId = insertFarm(tenantId, "Fazenda A");

        insertFarmScope(tenantId, membershipId, farmId);

        assertThat(queryLong("select count(*) from app.membership_farm_scopes")).isEqualTo(1);
    }

    @Test
    void shouldRejectCrossTenantMembershipAndFarmScopes() throws SQLException {
        UUID userId = insertUser();
        UUID tenantA = insertOrganization("Organização A");
        UUID tenantB = insertOrganization("Organização B");
        UUID membershipA = insertMembership(tenantA, userId, "OPERATOR", "SELECTED_FARMS");
        UUID membershipB = insertMembership(tenantB, userId, "OPERATOR", "SELECTED_FARMS");
        UUID farmA = insertFarm(tenantA, "Fazenda A");
        UUID farmB = insertFarm(tenantB, "Fazenda B");

        assertThatThrownBy(() -> insertFarmScope(tenantA, membershipA, farmB))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("membership_farm_scopes_farm_fk");
        assertThatThrownBy(() -> insertFarmScope(tenantA, membershipB, farmA))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("membership_farm_scopes_membership_fk");
    }

    @Test
    void shouldRejectDuplicateFarmScope() throws SQLException {
        UUID userId = insertUser();
        UUID tenantId = insertOrganization("Organização A");
        UUID membershipId = insertMembership(tenantId, userId, "VIEWER", "SELECTED_FARMS");
        UUID farmId = insertFarm(tenantId, "Fazenda A");
        insertFarmScope(tenantId, membershipId, farmId);

        assertThatThrownBy(() -> insertFarmScope(tenantId, membershipId, farmId))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("membership_farm_scopes_pk");
    }

    @Test
    void shouldHideTenantDataFromApiRoleWithoutTenantContext() throws SQLException {
        insertOrganization("Organização A");

        try (Connection connection = apiConnection()) {
            assertThat(queryLong(connection, "select count(*) from app.organizations")).isZero();
            connection.rollback();
        }
    }

    @Test
    void shouldExposeOnlyTheCurrentTenantToApiRole() throws SQLException {
        UUID tenantA = insertOrganization("Organização A");
        UUID tenantB = insertOrganization("Organização B");

        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            assertThat(queryUuids(connection, "select id from app.organizations"))
                    .containsExactly(tenantA);
            connection.rollback();
        }

        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantB);
            assertThat(queryUuids(connection, "select id from app.organizations"))
                    .containsExactly(tenantB);
            connection.rollback();
        }
    }

    @Test
    void shouldRejectInsertForTenantDifferentFromTransactionContext() throws SQLException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            assertThatThrownBy(() -> execute(connection, """
                    insert into app.organizations (id, name, status)
                    values (?, 'Organização B', 'ACTIVE')
                    """, tenantB))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security policy");
            connection.rollback();
        }
    }

    @Test
    void shouldReturnCurrentTenantInsideTransaction() throws SQLException {
        UUID tenantId = UUID.randomUUID();

        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantId);
            assertThat(currentTenant(connection)).isEqualTo(tenantId);
            connection.rollback();
        }
    }

    @Test
    void shouldClearLocalTenantSettingAfterTransactionEnds() throws SQLException {
        UUID tenantId = UUID.randomUUID();

        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantId);
            connection.commit();

            assertThat(currentTenant(connection)).isNull();
            connection.rollback();
        }
    }

    @Test
    void shouldNotReusePreviousTenantWhenNewTransactionChangesContext() throws SQLException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            assertThat(currentTenant(connection)).isEqualTo(tenantA);
            connection.commit();

            setTenant(connection, tenantB);
            assertThat(currentTenant(connection)).isEqualTo(tenantB);
            connection.rollback();
        }
    }

    private static UUID insertUser() throws SQLException {
        UUID userId = UUID.randomUUID();
        executeAsAdmin("insert into app.users (id, status) values (?, 'ACTIVE')", userId);
        return userId;
    }

    private static UUID insertOrganization(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.organizations (id, name, status)
                values (?, ?, 'ACTIVE')
                """, tenantId, name);
        return tenantId;
    }

    private static UUID insertFarm(UUID tenantId, String name) throws SQLException {
        UUID farmId = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.farms (id, tenant_id, name, status)
                values (?, ?, ?, 'ACTIVE')
                """, farmId, tenantId, name);
        return farmId;
    }

    private static UUID insertMembership(
            UUID tenantId,
            UUID userId,
            String role,
            String farmScopeMode
    ) throws SQLException {
        UUID membershipId = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.organization_memberships
                    (id, tenant_id, user_id, role_key, status, farm_scope_mode)
                values (?, ?, ?, ?, 'ACTIVE', ?)
                """, membershipId, tenantId, userId, role, farmScopeMode);
        return membershipId;
    }

    private static void insertFarmScope(UUID tenantId, UUID membershipId, UUID farmId) throws SQLException {
        executeAsAdmin("""
                insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id)
                values (?, ?, ?)
                """, tenantId, membershipId, farmId);
    }

    private static boolean queryBoolean(String sql) throws SQLException {
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private static long queryLong(String sql, Object... parameters) throws SQLException {
        try (Connection connection = adminConnection()) {
            return queryLong(connection, sql, parameters);
        }
    }

    private static long queryLong(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static UUID queryUuid(String sql, Object... parameters) throws SQLException {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static Set<String> queryStrings(String sql) throws SQLException {
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            Set<String> values = new HashSet<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        }
    }

    private static Set<UUID> queryUuids(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            Set<UUID> values = new HashSet<>();
            while (result.next()) {
                values.add(result.getObject(1, UUID.class));
            }
            return values;
        }
    }

    private static UUID currentTenant(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select app.current_tenant_id()")) {
            result.next();
            return result.getObject(1, UUID.class);
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}
