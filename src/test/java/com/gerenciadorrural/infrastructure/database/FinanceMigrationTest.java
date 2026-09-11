package com.gerenciadorrural.infrastructure.database;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.assertThat;

class FinanceMigrationTest extends PostgresMigrationTestSupport {
    @Test
    void protectsFinanceTablesWithForcedRlsAndAppendOnlyEvents() throws Exception {
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            for (String table : new String[]{"financial_categories", "financial_entries", "financial_entry_events"}) {
                try (ResultSet result = statement.executeQuery("select relrowsecurity, relforcerowsecurity from pg_class join pg_namespace on pg_namespace.oid=relnamespace where nspname='app' and relname='" + table + "'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                    assertThat(result.getBoolean(2)).isTrue();
                }
            }
            try (ResultSet result = statement.executeQuery("select has_table_privilege('public','app.financial_entry_events','select,insert,update,delete,truncate'), has_table_privilege('app_api','app.financial_entry_events','select,insert'), has_table_privilege('app_api','app.financial_entry_events','update,delete,truncate')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isFalse();
                assertThat(result.getBoolean(2)).isTrue();
                assertThat(result.getBoolean(3)).isFalse();
            }
        }
    }
}
