package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextResolutionMigrationTest extends PostgresMigrationTestSupport {

    @Test
    void appApiResolvesOnlyAnActiveAllFarmsContextAndReturnsPersistedValues() throws Exception {
        ContextData data = insertContext("MANAGER", "ALL_FARMS");

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, data.userId());
            try (PreparedStatement statement = connection.prepareStatement(
                    "select * from app.resolve_current_user_tenant_context(?, ?)")) {
                statement.setObject(1, data.organizationId());
                statement.setObject(2, data.farmId());
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getObject("organization_id", UUID.class)).isEqualTo(data.organizationId());
                    assertThat(result.getString("organization_name")).isEqualTo("Organização");
                    assertThat(result.getObject("farm_id", UUID.class)).isEqualTo(data.farmId());
                    assertThat(result.getString("farm_name")).isEqualTo("Fazenda");
                    assertThat(result.getObject("membership_id", UUID.class)).isEqualTo(data.membershipId());
                    assertThat(result.getString("role_key")).isEqualTo("MANAGER");
                    assertThat(result.getString("farm_scope_mode")).isEqualTo("ALL_FARMS");
                    assertThat(result.next()).isFalse();
                }
            }
        }
    }

    @Test
    void selectedFarmsCannotReuseAnotherUserMembershipOrCrossTenantFarm() throws Exception {
        ContextData userA = insertContext("VIEWER", "SELECTED_FARMS");
        ContextData userB = insertContext("OWNER", "SELECTED_FARMS");
        insertScope(userA);
        insertScope(userB);

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, userA.userId());
            assertThat(rows(connection, userA.organizationId(), userA.farmId())).isOne();
            assertThat(rows(connection, userB.organizationId(), userB.farmId())).isZero();
            assertThat(rows(connection, userA.organizationId(), userB.farmId())).isZero();
            assertThat(rows(connection, userB.organizationId(), userA.farmId())).isZero();
        }
    }

    @Test
    void selectedFarmsRequiresAnExplicitScopeForTheSameMembership() throws Exception {
        ContextData data = insertContext("VIEWER", "SELECTED_FARMS");

        try (Connection connection = apiConnection()) {
            assertThat(rows(connection, data.organizationId(), data.farmId())).isZero();
            setCurrentUser(connection, data.userId());
            assertThat(rows(connection, data.organizationId(), data.farmId())).isZero();
            insertScope(data);
            assertThat(rows(connection, data.organizationId(), data.farmId())).isOne();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MEMBERSHIP", "ORGANIZATION", "FARM"})
    void blockedStatusesNeverResolveAContext(String blockedEntity) throws Exception {
        ContextData data = insertContext("VIEWER", "ALL_FARMS");
        switch (blockedEntity) {
            case "USER" -> executeAsAdmin("update app.users set status = 'SUSPENDED' where id = ?", data.userId());
            case "MEMBERSHIP" -> executeAsAdmin(
                    "update app.organization_memberships set status = 'SUSPENDED' where id = ?", data.membershipId());
            case "ORGANIZATION" -> executeAsAdmin(
                    "update app.organizations set status = 'SUSPENDED' where id = ?", data.organizationId());
            case "FARM" -> executeAsAdmin("update app.farms set status = 'INACTIVE' where id = ?", data.farmId());
            default -> throw new IllegalArgumentException("Entidade de teste inválida");
        }

        try (Connection connection = apiConnection()) {
            setCurrentUser(connection, data.userId());
            assertThat(rows(connection, data.organizationId(), data.farmId())).isZero();
        }
    }

    @Test
    void resolverHasSafeSecurityDefinerConfigurationAndOnlyAppApiCanExecuteIt() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    select prosecdef, proconfig::text,
                           pg_get_functiondef('app.resolve_current_user_tenant_context(uuid, uuid)'::regprocedure)
                    from pg_proc
                    where oid = 'app.resolve_current_user_tenant_context(uuid, uuid)'::regprocedure
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isTrue();
                assertThat(result.getString(2)).contains("search_path=");
                assertThat(result.getString(3).toLowerCase())
                        .doesNotContain("insert ")
                        .doesNotContain("update ")
                        .doesNotContain("delete ");
            }
            statement.execute("create role anon nologin");
            statement.execute("create role authenticated nologin");
        }

        try (Connection connection = apiConnection()) {
            assertThat(rows(connection, UUID.randomUUID(), UUID.randomUUID())).isZero();
        }
        for (String role : new String[]{"anon", "authenticated"}) {
            try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                statement.execute("set role " + role);
                assertThatThrownBy(() -> statement.executeQuery(
                        "select * from app.resolve_current_user_tenant_context(null, null)"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    private static ContextData insertContext(String role, String farmScopeMode) throws SQLException {
        ContextData data = new ContextData(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        executeAsAdmin("insert into app.users (id, status) values (?, 'ACTIVE')", data.userId());
        executeAsAdmin("insert into app.organizations (id, name, status) values (?, 'Organização', 'ACTIVE')",
                data.organizationId());
        executeAsAdmin("insert into app.farms (id, tenant_id, name, status) values (?, ?, 'Fazenda', 'ACTIVE')",
                data.farmId(), data.organizationId());
        executeAsAdmin("""
                insert into app.organization_memberships
                    (id, tenant_id, user_id, role_key, status, farm_scope_mode)
                values (?, ?, ?, ?, 'ACTIVE', ?)
                """, data.membershipId(), data.organizationId(), data.userId(), role, farmScopeMode);
        return data;
    }

    private static void insertScope(ContextData data) throws SQLException {
        executeAsAdmin("""
                insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id)
                values (?, ?, ?)
                """, data.organizationId(), data.membershipId(), data.farmId());
    }

    private static void setCurrentUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.current_user_id', ?, true)")) {
            statement.setString(1, userId.toString());
            statement.execute();
        }
    }

    private static long rows(Connection connection, UUID organizationId, UUID farmId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from app.resolve_current_user_tenant_context(?, ?)")) {
            statement.setObject(1, organizationId);
            statement.setObject(2, farmId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private record ContextData(UUID userId, UUID organizationId, UUID farmId, UUID membershipId) {
    }
}
