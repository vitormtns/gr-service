package com.gerenciadorrural.infrastructure.database;

import static org.assertj.core.api.Assertions.*;

import com.gerenciadorrural.modules.herd.domain.MilkSession;
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

class MilkRecordRepositoryIntegrationTest extends PostgresMigrationTestSupport {
  @Test
  void aggregatesSevenDaysAndIsolatesFarmAndTenantWithRls() throws Exception {
    UUID tenant = UUID.randomUUID();
    UUID otherTenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    UUID otherFarm = UUID.randomUUID();
    UUID foreignFarm = UUID.randomUUID();
    UUID female = UUID.randomUUID();
    UUID otherFemale = UUID.randomUUID();
    UUID foreignFemale = UUID.randomUUID();
    executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE'),(?,?,'ACTIVE')",
        tenant, "Organização A", otherTenant, "Organização B");
    executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE'),(?,?,?,'ACTIVE'),(?,?,?,'ACTIVE')",
        farm, tenant, "Fazenda A", otherFarm, tenant, "Fazenda A2",
        foreignFarm, otherTenant, "Fazenda B");
    for (Object[] animal : new Object[][] {
        {female, tenant, farm, "A-1"},
        {otherFemale, tenant, otherFarm, "A-2"},
        {foreignFemale, otherTenant, foreignFarm, "B-1"}}) {
      executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status) values(?,?,?,?,'FEMALE','2024-01-01','ACTIVE')",
          animal);
    }
    LocalDate reference = LocalDate.of(2026, 9, 27);
    try (Connection connection = adminConnection()) {
      JdbcMilkRecordRepository repository = repository(connection);
      repository.insert(new TenantId(tenant), farm, female, UUID.randomUUID(),
          reference.minusDays(6), new BigDecimal("10"), MilkSession.MORNING, null, null);
      repository.insert(new TenantId(tenant), farm, female, UUID.randomUUID(),
          reference, new BigDecimal("20"), MilkSession.AFTERNOON, null, null);
      repository.insert(new TenantId(tenant), farm, female, UUID.randomUUID(),
          reference.minusDays(7), new BigDecimal("99"), null, null, null);
      repository.insert(new TenantId(tenant), otherFarm, otherFemale, UUID.randomUUID(),
          reference, new BigDecimal("40"), null, null, null);
      repository.insert(new TenantId(otherTenant), foreignFarm, foreignFemale, UUID.randomUUID(),
          reference, new BigDecimal("50"), null, null, null);
      assertThat(repository.overview(new TenantId(tenant), farm, reference).litersToday())
          .isEqualByComparingTo("20");
      assertThat(repository.overview(new TenantId(tenant), farm, reference)
          .femalesWithRecordToday()).isOne();
      assertThat(repository.overview(new TenantId(tenant), farm, reference)
          .averageLitersPerRecordLast7Days()).isEqualByComparingTo("15");
      assertThat(repository.animalStats(new TenantId(tenant), farm, female, reference)
          .recordsLast7Days()).isEqualTo(2);
      assertThat(repository.history(new TenantId(tenant), farm, female, 2, 0))
          .extracting(record -> record.liters().intValue()).containsExactly(20, 10);
      assertThat(repository.count(new TenantId(tenant), farm, female)).isEqualTo(3);
    }
    try (Connection connection = apiConnection()) {
      setTenant(connection, otherTenant);
      JdbcMilkRecordRepository repository = repository(connection);
      assertThat(repository.history(new TenantId(tenant), farm, female, 20, 0)).isEmpty();
      assertThat(repository.overview(new TenantId(tenant), farm, reference).litersToday())
          .isEqualByComparingTo("0");
      assertThatThrownBy(() -> repository.insert(new TenantId(tenant), farm, female,
          UUID.randomUUID(), reference, BigDecimal.ONE, null, null, null))
          .isInstanceOf(DataAccessException.class);
      connection.rollback();
    }
  }

  private static JdbcMilkRecordRepository repository(Connection connection) {
    return new JdbcMilkRecordRepository(
        new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
  }
}
