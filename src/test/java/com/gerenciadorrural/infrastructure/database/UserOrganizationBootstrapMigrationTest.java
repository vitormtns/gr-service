package com.gerenciadorrural.infrastructure.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserOrganizationBootstrapMigrationTest extends PostgresMigrationTestSupport {

    @Test
    void currentUserIsNullWithoutATransactionSetting() throws SQLException {
        try (Connection connection = apiConnection()) {
            assertThat(currentUser(connection)).isNull();
        }
    }

    @Test
    void currentUserIsAvailableOnlyInsideTheTransaction() throws SQLException {
        UUID userId = UUID.randomUUID();

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, userId);
            assertThat(currentUser(connection)).isEqualTo(userId);
            connection.commit();
            assertThat(currentUser(connection)).isNull();
        }
    }

    @Test
    void currentUserSettingDisappearsAfterRollback() throws SQLException {
        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, UUID.randomUUID());
            connection.rollback();
            assertThat(currentUser(connection)).isNull();
        }
    }

    @Test
    void currentUserSettingDoesNotLeakWhenHikariReusesTheSamePhysicalConnection() throws SQLException {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(POSTGRES.getJdbcUrl());
        configuration.setUsername(POSTGRES.getUsername());
        configuration.setPassword(POSTGRES.getPassword());
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(0);

        try (HikariDataSource dataSource = new HikariDataSource(configuration)) {
            int firstBackendPid;
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("set local role app_api");
                setCurrentUser(connection, UUID.randomUUID());
                firstBackendPid = backendPid(statement);
                connection.commit();
            }

            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("set local role app_api");
                assertThat(backendPid(statement)).isEqualTo(firstBackendPid);
                assertThat(currentUser(connection)).isNull();
                connection.rollback();
            }
        }
    }

    @Test
    void appApiCanExecuteTheBootstrapFunctionsButUntrustedRolesCannot() throws SQLException {
        try (Connection connection = apiConnection()) {
            assertThat(currentUser(connection)).isNull();
            assertThat(listOrganizations(connection)).isEmpty();
            connection.rollback();
        }

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create role anon nologin");
            statement.execute("create role authenticated nologin");
            statement.execute("create role bootstrap_untrusted nologin");
        }

        for (String role : List.of("anon", "authenticated", "bootstrap_untrusted")) {
            try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                statement.execute("set role " + role);
                assertThatThrownBy(() -> statement.executeQuery("select * from app.list_current_user_organizations()"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    void noOrganizationIsReturnedWithoutAUserContext() throws SQLException {
        UUID userId = insertUser("ACTIVE");
        insertAccessibleOrganization(userId, "Ativa", "ACTIVE", "ACTIVE", "OWNER", "ALL_FARMS");

        try (Connection connection = apiConnection()) {
            assertThat(listOrganizations(connection)).isEmpty();
        }
    }

    @Test
    void onlyTheCurrentUserMembershipsAreReturnedAcrossMultipleTenants() throws SQLException {
        UUID userA = insertUser("ACTIVE");
        UUID userB = insertUser("ACTIVE");
        UUID membershipA = insertAccessibleOrganization(userA, "Organização A", "ACTIVE", "ACTIVE", "OWNER", "ALL_FARMS");
        UUID membershipB = insertAccessibleOrganization(userA, "Organização B", "ACTIVE", "ACTIVE", "VIEWER", "SELECTED_FARMS");
        insertAccessibleOrganization(userB, "Organização de B", "ACTIVE", "ACTIVE", "ADMIN", "ALL_FARMS");

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, userA);
            List<OrganizationRow> organizations = listOrganizations(connection);

            assertThat(organizations).extracting(OrganizationRow::membershipId)
                    .containsExactly(membershipA, membershipB);
            assertThat(organizations).extracting(OrganizationRow::role)
                    .containsExactly("OWNER", "VIEWER");
            assertThat(organizations).extracting(OrganizationRow::farmScopeMode)
                    .containsExactly("ALL_FARMS", "SELECTED_FARMS");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "REVOKED"})
    void inactiveMembershipIsNotReturned(String membershipStatus) throws SQLException {
        UUID userId = insertUser("ACTIVE");
        insertAccessibleOrganization(userId, "Organização", "ACTIVE", membershipStatus, "OWNER", "ALL_FARMS");

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, userId);
            assertThat(listOrganizations(connection)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "ARCHIVED"})
    void inactiveOrganizationIsNotReturned(String organizationStatus) throws SQLException {
        UUID userId = insertUser("ACTIVE");
        insertAccessibleOrganization(userId, "Organização", organizationStatus, "ACTIVE", "OWNER", "ALL_FARMS");

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, userId);
            assertThat(listOrganizations(connection)).isEmpty();
        }
    }

    @Test
    void inactiveInternalUserIsNotReturned() throws SQLException {
        UUID userId = insertUser("SUSPENDED");
        insertAccessibleOrganization(userId, "Organização", "ACTIVE", "ACTIVE", "OWNER", "ALL_FARMS");

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, userId);
            assertThat(listOrganizations(connection)).isEmpty();
        }
    }

    @Test
    void bootstrapFunctionReturnsNoFarmDataAndIsReadOnly() throws SQLException {
        UUID userId = insertUser("ACTIVE");
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations (id, name, status) values (?, 'Organização', 'ACTIVE')", tenantId);
        executeAsAdmin("""
                insert into app.organization_memberships
                    (id, tenant_id, user_id, role_key, status, farm_scope_mode)
                values (?, ?, ?, 'OWNER', 'ACTIVE', 'ALL_FARMS')
                """, membershipId, tenantId, userId);
        executeAsAdmin("insert into app.farms (id, tenant_id, name, status) values (?, ?, 'Fazenda secreta', 'ACTIVE')",
                UUID.randomUUID(), tenantId);

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, userId);
            assertThat(listOrganizations(connection)).singleElement()
                    .satisfies(row -> assertThat(row.organizationName()).isEqualTo("Organização"));
        }

        assertThat(functionDefinition())
                .contains("select")
                .doesNotContain("insert ")
                .doesNotContain("update ")
                .doesNotContain("delete ")
                .doesNotContain("app.farms");
    }

    @Test
    void appApiRetainsNoBypassRlsAndBusinessTablesStayPrivate() throws SQLException {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select rolbypassrls from pg_roles where rolname = 'app_api'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isFalse();
        }
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select count(*)
                     from information_schema.tables
                     where table_schema = 'public'
                       and table_name in ('organizations', 'organization_memberships')
                     """)) {
            result.next();
            assertThat(result.getLong(1)).isZero();
        }
    }

    private static UUID insertUser(String status) throws SQLException {
        UUID userId = UUID.randomUUID();
        executeAsAdmin("insert into app.users (id, status) values (?, ?)", userId, status);
        return userId;
    }

    private static UUID insertAccessibleOrganization(
            UUID userId,
            String organizationName,
            String organizationStatus,
            String membershipStatus,
            String role,
            String farmScopeMode
    ) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations (id, name, status) values (?, ?, ?)",
                tenantId, organizationName, organizationStatus);
        executeAsAdmin("""
                insert into app.organization_memberships
                    (id, tenant_id, user_id, role_key, status, farm_scope_mode)
                values (?, ?, ?, ?, ?, ?)
                """, membershipId, tenantId, userId, role, membershipStatus, farmScopeMode);
        return membershipId;
    }

    private static void setCurrentUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.current_user_id', ?, true)")) {
            statement.setString(1, userId.toString());
            statement.execute();
        }
    }

    private static UUID currentUser(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select app.current_user_id()")) {
            result.next();
            return result.getObject(1, UUID.class);
        }
    }

    private static List<OrganizationRow> listOrganizations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select * from app.list_current_user_organizations()")) {
            List<OrganizationRow> organizations = new ArrayList<>();
            while (result.next()) {
                organizations.add(new OrganizationRow(
                        result.getObject("organization_id", UUID.class),
                        result.getString("organization_name"),
                        result.getObject("membership_id", UUID.class),
                        result.getString("role_key"),
                        result.getString("farm_scope_mode")
                ));
            }
            return organizations;
        }
    }

    private static String functionDefinition() throws SQLException {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select pg_get_functiondef('app.list_current_user_organizations()'::regprocedure)");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getString(1).toLowerCase();
        }
    }

    private static int backendPid(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("select pg_backend_pid()")) {
            result.next();
            return result.getInt(1);
        }
    }

    private record OrganizationRow(
            UUID organizationId,
            String organizationName,
            UUID membershipId,
            String role,
            String farmScopeMode
    ) {
    }
}
