package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;

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

class AccessibleFarmsMigrationTest extends PostgresMigrationTestSupport {

    @Test
    void functionIsRestrictedAndReturnsNothingWithoutUserContext() throws SQLException {
        UUID organizationId = UUID.randomUUID();
        try (Connection connection = apiConnection()) {
            assertThat(farms(connection, organizationId)).isEmpty();
        }
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create role anon nologin");
            statement.execute("create role authenticated nologin");
            for (String role : List.of("anon", "authenticated")) {
                statement.execute("set role " + role);
                assertThatThrownBy(() -> statement.executeQuery(
                        "select * from app.list_current_user_accessible_farms('" + organizationId + "'::uuid)"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
                statement.execute("reset role");
            }
        }
    }

    @Test
    void allFarmsReturnsOnlyActiveFarmsInDeterministicOrder() throws SQLException {
        UUID userId = user();
        UUID organizationId = organization("Organização A", "ACTIVE");
        membership(organizationId, userId, "ACTIVE", "ALL_FARMS");
        UUID first = farm(organizationId, "Alfa", "ACTIVE");
        UUID second = farm(organizationId, "Zeta", "ACTIVE");
        farm(organizationId, "Inativa", "INACTIVE");
        farm(organizationId, "Arquivada", "ARCHIVED");

        try (Connection connection = apiConnection()) {
            context(connection, userId);
            assertThat(farms(connection, organizationId)).containsExactly(
                    new FarmRow(first, "Alfa"), new FarmRow(second, "Zeta"));
        }
    }

    @Test
    void selectedFarmsUsesOnlyTheCurrentMembershipScopeAndTenant() throws SQLException {
        UUID userId = user();
        UUID organizationId = organization("Organização A", "ACTIVE");
        UUID membershipId = membership(organizationId, userId, "ACTIVE", "SELECTED_FARMS");
        UUID selected = farm(organizationId, "Selecionada", "ACTIVE");
        farm(organizationId, "Não selecionada", "ACTIVE");
        scope(organizationId, membershipId, selected);
        UUID otherUser = user();
        UUID otherMembership = membership(organizationId, otherUser, "ACTIVE", "SELECTED_FARMS");
        UUID otherScopedFarm = farm(organizationId, "Escopo de outra pessoa", "ACTIVE");
        scope(organizationId, otherMembership, otherScopedFarm);

        try (Connection connection = apiConnection()) {
            context(connection, userId);
            assertThat(farms(connection, organizationId)).containsExactly(new FarmRow(selected, "Selecionada"));
        }
    }

    @Test
    void inaccessibleOrBlockedOrganizationAndMembershipReturnAnIndistinguishableEmptyList() throws SQLException {
        UUID userId = user();
        UUID noMembershipOrganization = organization("Sem membership", "ACTIVE");
        farm(noMembershipOrganization, "Fazenda", "ACTIVE");
        UUID suspendedMembershipOrganization = organization("Membership suspenso", "ACTIVE");
        membership(suspendedMembershipOrganization, userId, "SUSPENDED", "ALL_FARMS");
        farm(suspendedMembershipOrganization, "Fazenda", "ACTIVE");
        UUID archivedOrganization = organization("Arquivada", "ARCHIVED");
        membership(archivedOrganization, userId, "ACTIVE", "ALL_FARMS");
        farm(archivedOrganization, "Fazenda", "ACTIVE");

        try (Connection connection = apiConnection()) {
            context(connection, userId);
            assertThat(farms(connection, noMembershipOrganization)).isEmpty();
            assertThat(farms(connection, suspendedMembershipOrganization)).isEmpty();
            assertThat(farms(connection, archivedOrganization)).isEmpty();
        }
    }

    @Test
    void currentUserContextDisappearsAfterCommitAndRollback() throws SQLException {
        try (Connection connection = apiConnection()) {
            context(connection, UUID.randomUUID());
            connection.commit();
            assertThat(currentUser(connection)).isNull();
            context(connection, UUID.randomUUID());
            connection.rollback();
            assertThat(currentUser(connection)).isNull();
        }
    }

    private static UUID user() throws SQLException { UUID id = UUID.randomUUID(); executeAsAdmin("insert into app.users (id, status) values (?, 'ACTIVE')", id); return id; }
    private static UUID organization(String name, String status) throws SQLException { UUID id = UUID.randomUUID(); executeAsAdmin("insert into app.organizations (id, name, status) values (?, ?, ?)", id, name, status); return id; }
    private static UUID membership(UUID organizationId, UUID userId, String status, String mode) throws SQLException { UUID id = UUID.randomUUID(); executeAsAdmin("insert into app.organization_memberships (id, tenant_id, user_id, role_key, status, farm_scope_mode) values (?, ?, ?, 'VIEWER', ?, ?)", id, organizationId, userId, status, mode); return id; }
    private static UUID farm(UUID organizationId, String name, String status) throws SQLException { UUID id = UUID.randomUUID(); executeAsAdmin("insert into app.farms (id, tenant_id, name, status) values (?, ?, ?, ?)", id, organizationId, name, status); return id; }
    private static void scope(UUID organizationId, UUID membershipId, UUID farmId) throws SQLException { executeAsAdmin("insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id) values (?, ?, ?)", organizationId, membershipId, farmId); }
    private static void context(Connection connection, UUID userId) throws SQLException { try (PreparedStatement statement = connection.prepareStatement("select set_config('app.current_user_id', ?, true)")) { statement.setString(1, userId.toString()); statement.execute(); } }
    private static UUID currentUser(Connection connection) throws SQLException { try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select app.current_user_id()")) { result.next(); return result.getObject(1, UUID.class); } }
    private static List<FarmRow> farms(Connection connection, UUID organizationId) throws SQLException { try (PreparedStatement statement = connection.prepareStatement("select * from app.list_current_user_accessible_farms(?)")) { statement.setObject(1, organizationId); try (ResultSet result = statement.executeQuery()) { List<FarmRow> rows = new ArrayList<>(); while (result.next()) rows.add(new FarmRow(result.getObject("farm_id", UUID.class), result.getString("farm_name"))); return rows; } } }
    private record FarmRow(UUID id, String name) { }
}
