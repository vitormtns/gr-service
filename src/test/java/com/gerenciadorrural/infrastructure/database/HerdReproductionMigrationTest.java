package com.gerenciadorrural.infrastructure.database;

import com.gerenciadorrural.modules.herd.domain.HerdReproductionRepository;
import com.gerenciadorrural.modules.herd.domain.PregnancyStatus;
import com.gerenciadorrural.modules.herd.domain.ReproductionServiceType;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdReproductionRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HerdReproductionMigrationTest extends PostgresMigrationTestSupport {

    @Test
    void shouldEnforceOneOpenPregnancyAndTenantBoundMaternalPregnancy() throws SQLException {
        Fixture a = fixture();
        Fixture b = fixture();
        UUID mother = animal(a, a.farmA(), "MAE-A");
        UUID foreignMother = animal(b, b.farmA(), "MAE-B");
        UUID foreignCalf = animal(b, b.farmA(), "CRIA-B");
        UUID pregnancy = pregnancy(a, mother, UUID.randomUUID());

        assertThatThrownBy(() -> pregnancy(a, mother, UUID.randomUUID()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("animal_pregnancies_open_mother_unique");
        assertThatThrownBy(() -> executeAsAdmin("""
                insert into app.animal_maternal_relations(tenant_id,mother_animal_id,calf_animal_id,pregnancy_id)
                values (?, ?, ?, ?)
                """, b.tenant(), foreignMother, foreignCalf, pregnancy))
                .isInstanceOf(SQLException.class);
        assertThat(pregnancy).isNotNull();
    }

    @Test
    void shouldApplyRlsAndMinimumGrantsToPregnanciesAndMaternalRelations() throws SQLException {
        Fixture a = fixture();
        Fixture b = fixture();
        UUID motherA = animal(a, a.farmA(), "MAE-A");
        UUID motherB = animal(b, b.farmA(), "MAE-B");
        pregnancy(a, motherA, UUID.randomUUID());
        pregnancy(b, motherB, UUID.randomUUID());

        try (Connection connection = apiConnection()) {
            setTenant(connection, a.tenant());
            assertThat(count(connection, "select count(*) from app.animal_pregnancies")).isEqualTo(1);
            assertThat(count(connection, "select count(*) from app.animal_maternal_relations")).isZero();
            assertThatThrownBy(() -> execute(connection, """
                    insert into app.animal_pregnancies(id,tenant_id,farm_id,mother_animal_id,service_type,service_on,expected_calving_on,operation_id,command_payload)
                    values (?, ?, ?, ?, 'INSEMINATION', current_date, current_date + 283, ?, '{}')
                    """, UUID.randomUUID(), b.tenant(), b.farmA(), motherB, UUID.randomUUID()))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security policy");
            connection.rollback();
        }

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                select relname, relrowsecurity, relforcerowsecurity,
                       has_table_privilege('app_api', 'app.' || relname, 'select') as can_select,
                       has_table_privilege('app_api', 'app.' || relname, 'insert') as can_insert,
                       has_table_privilege('app_api', 'app.' || relname, 'update') as can_update,
                       has_table_privilege('public', 'app.' || relname, 'select') as public_select
                from pg_class join pg_namespace on pg_namespace.oid = pg_class.relnamespace
                where nspname='app' and relname in ('animal_pregnancies','animal_maternal_relations')
                order by relname
                """)) {
            while (result.next()) {
                assertThat(result.getBoolean("relrowsecurity")).isTrue();
                assertThat(result.getBoolean("relforcerowsecurity")).isTrue();
                assertThat(result.getBoolean("can_select")).isTrue();
                assertThat(result.getBoolean("can_insert")).isTrue();
                assertThat(result.getBoolean("can_update"))
                        .isEqualTo(result.getString("relname").equals("animal_pregnancies"));
                assertThat(result.getBoolean("public_select")).isFalse();
            }
        }
    }

    @Test
    void shouldRejectInvalidPregnancyStateShapes() throws Exception {
        Fixture fixture = fixture();
        UUID mother = animal(fixture, fixture.farmA(), "MAE");
        UUID pregnancy = pregnancy(fixture, mother, UUID.randomUUID());

        assertThatThrownBy(() -> executeAsAdmin("""
                update app.animal_pregnancies set status='CONFIRMED' where id=?
                """, pregnancy))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("animal_pregnancies_state_shape_check");
        assertThatThrownBy(() -> executeAsAdmin("""
                update app.animal_pregnancies set status='CALVED',calf_animal_id=? where id=?
                """, mother, pregnancy))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("animal_pregnancies_state_shape_check");
    }

    @Test
    void shouldKeepServiceFarmHistoricalAndUseMothersCurrentCustodyForReproduction() throws Exception {
        Fixture fixture = fixture();
        UUID mother = animal(fixture, fixture.farmA(), "MAE");
        UUID id = pregnancy(fixture, mother, UUID.randomUUID());
        executeAsAdmin("update app.animals set farm_id=? where tenant_id=? and id=?", fixture.farmB(), fixture.tenant(), mother);

        try (Connection connection = adminConnection()) {
            var repository = new JdbcHerdReproductionRepository(new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
            var found = repository.find(new TenantId(fixture.tenant()), fixture.farmB(), id, true);
            assertThat(found).isPresent();
            assertThat(found.orElseThrow().farmId()).isEqualTo(fixture.farmA());
            assertThat(repository.transition(new TenantId(fixture.tenant()), fixture.farmB(), id, 0,
                    PregnancyStatus.CONFIRMED, LocalDate.now(), null, null)).isPresent();
            assertThat(repository.find(new TenantId(fixture.tenant()), fixture.farmA(), id, false)).isEmpty();
        }
    }

    private static Fixture fixture() throws SQLException {
        UUID tenant = UUID.randomUUID();
        UUID farmA = UUID.randomUUID();
        UUID farmB = UUID.randomUUID();
        executeAsAdmin("insert into app.organizations(id,name,status) values (?, ?, 'ACTIVE')", tenant, "Organização");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values (?, ?, ?, 'ACTIVE')", farmA, tenant, "Fazenda A");
        executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values (?, ?, ?, 'ACTIVE')", farmB, tenant, "Fazenda B");
        return new Fixture(tenant, farmA, farmB);
    }

    private static UUID animal(Fixture fixture, UUID farm, String identification) throws SQLException {
        UUID id = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.animals(id,tenant_id,farm_id,identification,sex,status)
                values (?, ?, ?, ?, 'FEMALE', 'ACTIVE')
                """, id, fixture.tenant(), farm, identification + UUID.randomUUID());
        return id;
    }

    private static UUID pregnancy(Fixture fixture, UUID mother, UUID operation) throws SQLException {
        UUID id = UUID.randomUUID();
        executeAsAdmin("""
                insert into app.animal_pregnancies(id,tenant_id,farm_id,mother_animal_id,service_type,service_on,expected_calving_on,operation_id,command_payload)
                values (?, ?, ?, ?, 'INSEMINATION', current_date, current_date + 283, ?, '{}')
                """, id, fixture.tenant(), fixture.farmA(), mother, operation);
        return id;
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private record Fixture(UUID tenant, UUID farmA, UUID farmB) { }
}
