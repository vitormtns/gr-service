package com.gerenciadorrural.infrastructure.database;

import static org.assertj.core.api.Assertions.*;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdAnimalQueryRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class HerdLocationQueryIntegrationTest extends PostgresMigrationTestSupport {
  @Test
  void filtersLocationAndCountsAcrossPagesWithoutMixingFarmsOrTenants() throws Exception {
    UUID tenant = UUID.randomUUID(), foreignTenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID(), otherFarm = UUID.randomUUID(), foreignFarm = UUID.randomUUID(), paddock = UUID.randomUUID();
    executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE'),(?,?,'ACTIVE')", tenant, "Organização de teste", foreignTenant, "Outra organização");
    executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE'),(?,?,?,'ACTIVE'),(?,?,?,'ACTIVE')", farm, tenant, "Fazenda", otherFarm, tenant, "Outra fazenda", foreignFarm, foreignTenant, "Fazenda externa");
    executeAsAdmin("insert into app.paddocks(id,tenant_id,farm_id,name,status) values(?,?,?,?,'ACTIVE')", paddock, tenant, farm, "Piquete");
    insert(tenant, farm, "A-001", "ACTIVE", null);
    insert(tenant, farm, "A-002", "ACTIVE", null);
    insert(tenant, farm, "A-003", "ACTIVE", paddock);
    insert(tenant, farm, "A-004", "SOLD", null);
    insert(tenant, otherFarm, "B-001", "ACTIVE", null);
    insert(foreignTenant, foreignFarm, "C-001", "ACTIVE", null);
    try (Connection connection = apiConnection()) {
      setTenant(connection, tenant);
      var repository = new JdbcHerdAnimalQueryRepository(new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)));
      var first = repository.list(new TenantId(tenant), farm, new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 0, 1, true));
      assertThat(first.totalElements()).isEqualTo(2);
      assertThat(first.totalPages()).isEqualTo(2);
      assertThat(first.items()).extracting(HerdAnimalSummary::identification).containsExactly("A-001");
      var second = repository.list(new TenantId(tenant), farm, new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 1, 1, true));
      assertThat(second.totalElements()).isEqualTo(2);
      assertThat(second.items()).extracting(HerdAnimalSummary::identification).containsExactly("A-002");
      var located = repository.list(new TenantId(tenant), farm, new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 0, 20, false));
      assertThat(located.totalElements()).isOne();
      assertThat(located.items()).extracting(HerdAnimalSummary::identification).containsExactly("A-003");
      assertThat(repository.list(new TenantId(tenant), farm, new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 0, 20)).totalElements()).isEqualTo(3);
      assertThat(repository.list(new TenantId(tenant), otherFarm, new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 0, 20, true)).totalElements()).isOne();
      assertThat(repository.list(new TenantId(tenant), foreignFarm, new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 0, 20, true)).totalElements()).isZero();
      assertThat(repository.list(new TenantId(foreignTenant), foreignFarm, new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 0, 20, true)).totalElements()).isZero();
    }
  }

  private void insert(UUID tenant, UUID farm, String identification, String status, UUID paddock) throws Exception {
    executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,status,paddock_id) values(?,?,?,?,'FEMALE',?,?)", UUID.randomUUID(), tenant, farm, identification, status, paddock);
  }
}
