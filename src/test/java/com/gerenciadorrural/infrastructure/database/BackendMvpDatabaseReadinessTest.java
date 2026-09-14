package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackendMvpDatabaseReadinessTest extends PostgresMigrationTestSupport {

    @Test
    void allApplicationTablesEnforceRowLevelSecurity() throws Exception {
        List<String> unprotectedTables = strings("""
                select c.relname
                  from pg_catalog.pg_class c
                  join pg_catalog.pg_namespace n on n.oid = c.relnamespace
                  join information_schema.columns col
                    on col.table_schema = n.nspname
                   and col.table_name = c.relname
                   and col.column_name = 'tenant_id'
                 where n.nspname = 'app'
                   and c.relkind = 'r'
                   and (not c.relrowsecurity or not c.relforcerowsecurity)
                 order by c.relname
                """);

        assertThat(unprotectedTables)
                .as("tabelas de aplicação sem RLS habilitada e forçada")
                .isEmpty();
    }

    @Test
    void securityDefinerFunctionsHaveFixedSearchPathAndNoPublicExecute() throws Exception {
        List<String> unsafeFunctions = strings("""
                select p.proname || '(' || pg_catalog.pg_get_function_identity_arguments(p.oid) || ')'
                  from pg_catalog.pg_proc p
                  join pg_catalog.pg_namespace n on n.oid = p.pronamespace
                 where n.nspname = 'app'
                   and p.prosecdef
                   and (
                       not coalesce(array_to_string(p.proconfig, ','), '') like '%search_path=%'
                       or exists (
                           select 1
                             from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                            where acl.grantee = 0
                              and acl.privilege_type = 'EXECUTE'
                       )
                   )
                 order by p.proname
                """);

        assertThat(unsafeFunctions)
                .as("funções SECURITY DEFINER com search_path inseguro ou execução pública")
                .isEmpty();
    }

    @Test
    void runtimeRoleKeepsLeastPrivilegeGuardrails() throws Exception {
        List<String> excessivePrivileges = strings("""
                select privilege_type || ' em ' || table_name
                  from information_schema.role_table_grants
                 where grantee = 'app_api'
                   and table_schema = 'app'
                   and privilege_type in ('TRUNCATE', 'TRIGGER', 'REFERENCES')
                union all
                select 'CREATE no schema app'
                 where has_schema_privilege('app_api', 'app', 'CREATE')
                union all
                select 'atributo privilegiado da role app_api'
                  from pg_catalog.pg_roles
                 where rolname = 'app_api'
                   and (rolsuper or rolcreaterole or rolcreatedb or rolcanlogin or rolreplication or rolbypassrls)
                order by 1
                """);

        assertThat(excessivePrivileges)
                .as("privilégios excessivos da role de runtime")
                .isEmpty();
    }

    @Test
    void schemaHasNoStructurallyDuplicateIndexes() throws Exception {
        List<String> duplicates = strings("""
                select c.relname || ': ' || ci1.relname || ' = ' || ci2.relname
                  from pg_catalog.pg_index i1
                  join pg_catalog.pg_index i2
                    on i1.indrelid = i2.indrelid
                   and i1.indexrelid < i2.indexrelid
                   and i1.indkey = i2.indkey
                   and i1.indclass = i2.indclass
                   and i1.indcollation = i2.indcollation
                   and i1.indoption = i2.indoption
                   and i1.indexprs is not distinct from i2.indexprs
                   and i1.indpred is not distinct from i2.indpred
                  join pg_catalog.pg_class c on c.oid = i1.indrelid
                  join pg_catalog.pg_namespace n on n.oid = c.relnamespace
                  join pg_catalog.pg_class ci1 on ci1.oid = i1.indexrelid
                  join pg_catalog.pg_class ci2 on ci2.oid = i2.indexrelid
                 where n.nspname = 'app'
                 order by c.relname, ci1.relname
                """);

        assertThat(duplicates)
                .as("índices estruturalmente duplicados")
                .isEmpty();
    }

    @Test
    void everyTenantTableHasAnIndexStartingWithTenantId() throws Exception {
        List<String> missingIndexes = strings("""
                select distinct c.relname
                  from pg_catalog.pg_class c
                  join pg_catalog.pg_namespace n on n.oid = c.relnamespace
                  join information_schema.columns col
                    on col.table_schema = n.nspname
                   and col.table_name = c.relname
                   and col.column_name = 'tenant_id'
                 where n.nspname = 'app'
                   and c.relkind = 'r'
                   and not exists (
                       select 1
                         from pg_catalog.pg_index i
                        where i.indrelid = c.oid
                          and (i.indkey::int2[])[0] = (
                              select a.attnum
                                from pg_catalog.pg_attribute a
                               where a.attrelid = c.oid
                                 and a.attname = 'tenant_id'
                          )
                   )
                 order by c.relname
                """);

        assertThat(missingIndexes)
                .as("tabelas multi-tenant sem índice iniciado por tenant_id")
                .isEmpty();
    }

    private static List<String> strings(String sql) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }
}
