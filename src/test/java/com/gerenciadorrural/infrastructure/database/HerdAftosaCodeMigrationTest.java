package com.gerenciadorrural.infrastructure.database;

import static org.assertj.core.api.Assertions.*;

import com.gerenciadorrural.modules.herd.domain.HealthProcedureCode;
import com.gerenciadorrural.modules.herd.domain.HealthTreatmentType;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdManagementRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class HerdAftosaCodeMigrationTest extends PostgresMigrationTestSupport {
  @Test
  void storesStructuredHistoricalCodeWithoutDueDateAndEnforcesTenantRls() throws Exception {
    UUID tenant = UUID.randomUUID(), otherTenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID(), animal = UUID.randomUUID();
    executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE'),(?,?,'ACTIVE')",
        tenant, "Organização A", otherTenant, "Organização B");
    executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE')",
        farm, tenant, "Fazenda A");
    executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,status) values(?,?,?,'A-1','FEMALE','ACTIVE')",
        animal, tenant, farm);
    TenantId tenantId = new TenantId(tenant);
    try (Connection connection = apiConnection()) {
      setTenant(connection, tenant);
      JdbcHerdManagementRepository repository = repository(connection);
      repository.treatment(tenantId, farm, animal, UUID.randomUUID(),
          HealthTreatmentType.VACCINATION, HealthProcedureCode.FOOT_AND_MOUTH_DISEASE,
          LocalDate.of(2020, 1, 1), null, null, null, null, null);
      assertThat(repository.treatments(tenantId, farm, animal, 20, 0)).singleElement()
          .satisfies(item -> {
            assertThat(item.procedureCode()).isEqualTo(HealthProcedureCode.FOOT_AND_MOUTH_DISEASE);
            assertThat(item.nextDueOn()).isNull();
          });
      connection.commit();
    }
    try (Connection connection = apiConnection()) {
      setTenant(connection, otherTenant);
      JdbcHerdManagementRepository repository = repository(connection);
      assertThat(repository.treatments(tenantId, farm, animal, 20, 0)).isEmpty();
      assertThatThrownBy(() -> repository.treatment(tenantId, farm, animal,
          UUID.randomUUID(), HealthTreatmentType.VACCINATION,
          HealthProcedureCode.FOOT_AND_MOUTH_DISEASE, LocalDate.of(2020, 1, 1),
          null, null, null, null, null)).isInstanceOf(DataAccessException.class);
      connection.rollback();
    }
  }

  private static JdbcHerdManagementRepository repository(Connection connection) {
    return new JdbcHerdManagementRepository(new NamedParameterJdbcTemplate(
        new SingleConnectionDataSource(connection, true)));
  }
}
