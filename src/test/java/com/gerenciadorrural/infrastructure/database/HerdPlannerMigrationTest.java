package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HerdPlannerMigrationTest extends PostgresMigrationTestSupport {

    @Test
    void enforcesPlannerLifecycleOperationHistoryMinimumGrantsAndRls() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID farmA = UUID.randomUUID();
        UUID farmB = UUID.randomUUID();
        UUID secondFarmA = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        organization(tenantA, farmA);
        organization(tenantB, farmB);
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                secondFarmA, tenantA, "Segunda fazenda");
        executeAsAdmin("""
                insert into app.herd_planner_items
                  (id,tenant_id,farm_id,type,title,scheduled_for,created_by)
                values(?,?,?,'WEIGHING','Pesar lote',?,?)
                """, item, tenantA, farmA, LocalDate.now(), UUID.randomUUID());
        executeAsAdmin("""
                insert into app.herd_planner_operations
                  (tenant_id,farm_id,operation_id,operation_type,planner_item_id,command_payload,resulting_version)
                values(?,?,?,'CREATE',?,'{}',0)
                """, tenantA, farmA, operation, item);

        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.herd_planner_operations
                  (tenant_id,farm_id,operation_id,operation_type,planner_item_id,command_payload,resulting_version)
                values(?,?,?,'COMPLETE',?,'{}',1)
                """, tenantA, farmA, operation, item)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.herd_planner_operations
                  (tenant_id,farm_id,operation_id,operation_type,planner_item_id,command_payload,resulting_version)
                values(?,?,?,'CREATE',?,'{}',0)
                """, tenantA, secondFarmA, UUID.randomUUID(), item)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeAsAdmin(
                "update app.herd_planner_items set status='COMPLETED' where id=?", item))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.herd_planner_items
                  (id,tenant_id,farm_id,type,title,notes,scheduled_for,created_by)
                values(?,?,?,'GENERAL','Título',?, ?,?)
                """, UUID.randomUUID(), tenantA, farmA, " ", LocalDate.now(), UUID.randomUUID()))
                .isInstanceOf(SQLException.class);

        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantA);
            assertThat(count(connection, "app.herd_planner_items")).isOne();
            assertThat(count(connection, "app.herd_planner_operations")).isOne();
            connection.rollback();
        }
        try (Connection connection = apiConnection()) {
            setTenant(connection, tenantB);
            assertThat(count(connection, "app.herd_planner_items")).isZero();
            assertThat(count(connection, "app.herd_planner_operations")).isZero();
            connection.rollback();
        }

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select relname,relrowsecurity,relforcerowsecurity,
                            has_table_privilege('app_api','app.'||relname,'select,insert'),
                            has_table_privilege('app_api','app.'||relname,'delete,truncate,references,trigger')
                       from pg_class join pg_namespace n on n.oid=relnamespace
                      where nspname='app'
                        and relname in ('herd_planner_items','herd_planner_operations')
                      order by relname
                     """)) {
            int rows = 0;
            while (result.next()) {
                rows++;
                assertThat(result.getBoolean(2)).isTrue();
                assertThat(result.getBoolean(3)).isTrue();
                assertThat(result.getBoolean(4)).isTrue();
                assertThat(result.getBoolean(5)).isFalse();
            }
            assertThat(rows).isEqualTo(2);
        }
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     select has_column_privilege('app_api','app.herd_planner_items','tenant_id','update'),
                            has_column_privilege('app_api','app.herd_planner_items','farm_id','update'),
                            has_column_privilege('app_api','app.herd_planner_items','created_by','update')
                     """)) {
            result.next();
            assertThat(result.getBoolean(1)).isFalse();
            assertThat(result.getBoolean(2)).isFalse();
            assertThat(result.getBoolean(3)).isFalse();
        }
    }

    private void organization(UUID tenant, UUID farm) throws Exception {
        executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')", tenant, "Organização");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
                farm, tenant, "Fazenda");
    }

    private long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select count(*) from " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
