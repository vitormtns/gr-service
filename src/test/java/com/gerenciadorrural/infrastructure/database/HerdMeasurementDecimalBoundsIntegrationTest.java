package com.gerenciadorrural.infrastructure.database;

import static org.assertj.core.api.Assertions.*;

import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdManagementRepository;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcMilkRecordRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Confere os limites das migrations reais em um PostgreSQL descartável de teste. */
class HerdMeasurementDecimalBoundsIntegrationTest extends PostgresMigrationTestSupport {
  @Test
  void storesTheExactUpperBoundsAndRejectsTheNextIntegerUnderAppApi() throws Exception {
    UUID tenant = UUID.randomUUID(), farm = UUID.randomUUID(), animal = UUID.randomUUID();
    executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE')", tenant, "Teste de medidas");
    executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')", farm, tenant, "Fazenda de teste");
    executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,status) values(?,?,?,?,'FEMALE','ACTIVE')", animal, tenant, farm, "MEDIDA-TESTE");
    TenantId scope = new TenantId(tenant);
    LocalDate day = LocalDate.of(2026, 9, 29);
    try (Connection connection = apiConnection()) {
      var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
      var weights = new JdbcHerdManagementRepository(jdbc);
      var milk = new JdbcMilkRecordRepository(jdbc);
      setTenant(connection, tenant);
      weights.weight(scope, farm, animal, UUID.randomUUID(), day, new BigDecimal("99999.999"), null, null);
      milk.insert(scope, farm, animal, UUID.randomUUID(), day, new BigDecimal("999999.999"), null, null, null);
      assertThat(weights.weights(scope, farm, animal, 20, 0).getFirst().weightKg()).isEqualByComparingTo("99999.999");
      assertThat(milk.history(scope, farm, animal, 20, 0).getFirst().liters()).isEqualByComparingTo("999999.999");
      connection.commit();
      setTenant(connection, tenant);
      assertThatThrownBy(() -> weights.weight(scope, farm, animal, UUID.randomUUID(), day, new BigDecimal("100000"), null, null)).isInstanceOf(DataAccessException.class);
      connection.rollback();
      setTenant(connection, tenant);
      assertThatThrownBy(() -> milk.insert(scope, farm, animal, UUID.randomUUID(), day, new BigDecimal("1000000"), null, null, null)).isInstanceOf(DataAccessException.class);
      connection.rollback();
      setTenant(connection, tenant);
      assertThat(weights.weightCount(scope, farm, animal)).isOne();
      assertThat(milk.count(scope, farm, animal)).isOne();
    }
  }
}
