package com.gerenciadorrural.infrastructure.database;

import static org.assertj.core.api.Assertions.*;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.infrastructure.JdbcHerdGroupRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class HerdGroupRepositoryIntegrationTest extends PostgresMigrationTestSupport {
  @Test
  void manualAndSmartMembershipRespectFarmAndTenantAndCurrentPregnancy() throws Exception {
    UUID tenant = UUID.randomUUID(), otherTenant = UUID.randomUUID();
    UUID farm = UUID.randomUUID(), otherFarm = UUID.randomUUID(), foreignFarm = UUID.randomUUID();
    UUID female = UUID.randomUUID(), male = UUID.randomUUID(), otherAnimal = UUID.randomUUID();
    UUID foreignAnimal = UUID.randomUUID();
    executeAsAdmin("insert into app.organizations(id,name,status) values(?,?,'ACTIVE'),(?,?,'ACTIVE')",
        tenant, "Organização A", otherTenant, "Organização B");
    executeAsAdmin("insert into app.farms(id,tenant_id,name,status) values(?,?,?,'ACTIVE'),(?,?,?,'ACTIVE'),(?,?,?,'ACTIVE')",
        farm, tenant, "Fazenda A", otherFarm, tenant, "Fazenda A2", foreignFarm, otherTenant, "Fazenda B");
    for (Object[] item : new Object[][] {
        {female, tenant, farm, "F-1", "FEMALE", LocalDate.of(2023, 1, 1)},
        {male, tenant, farm, "M-1", "MALE", LocalDate.of(2023, 1, 1)},
        {otherAnimal, tenant, otherFarm, "F-2", "FEMALE", LocalDate.of(2023, 1, 1)},
        {foreignAnimal, otherTenant, foreignFarm, "F-3", "FEMALE", LocalDate.of(2023, 1, 1)}})
      executeAsAdmin("insert into app.animals(id,tenant_id,farm_id,identification,sex,birth_date,status) values(?,?,?,?,?,?,'ACTIVE')", item);
    UUID pregnancy = UUID.randomUUID();
    executeAsAdmin("insert into app.animal_pregnancies(id,tenant_id,farm_id,mother_animal_id,service_type,service_on,expected_calving_on,status,confirmed_on,operation_id,command_payload) values(?,?,?,?,'INSEMINATION','2026-01-01','2026-10-01','CONFIRMED','2026-02-01',?,'{}')",
        pregnancy, tenant, farm, female, UUID.randomUUID());

    TenantId tenantId = new TenantId(tenant);
    UUID manualId = UUID.randomUUID(), smartId = UUID.randomUUID();
    try (Connection connection = adminConnection()) {
      JdbcHerdGroupRepository repository = repository(connection);
      HerdGroup manual = repository.insert(tenantId, farm, new HerdGroup(manualId, "Lote A",
          HerdGroup.Kind.MANUAL, HerdGroup.Status.ACTIVE, HerdGroup.Rules.empty(), 0));
      assertThat(repository.animalExists(tenantId, farm, otherAnimal)).isFalse();
      repository.addMember(tenantId, farm, manualId, female);
      assertThatThrownBy(() -> repository.addMember(tenantId, farm, manualId, otherAnimal))
          .isInstanceOf(DataAccessException.class);
      assertThat(repository.animals(tenantId, farm, manual, LocalDate.of(2026, 9, 27), 0, 20)
          .items()).extracting(HerdAnimalSummary::id).containsExactly(female);
      assertThat(repository.bumpVersion(tenantId, farm, manualId, 0)).get()
          .extracting(HerdGroup::version).isEqualTo(1L);
      assertThat(repository.bumpVersion(tenantId, farm, manualId, 0)).isEmpty();

      HerdGroup smart = repository.insert(tenantId, farm, new HerdGroup(smartId, "Reprodutivas",
          HerdGroup.Kind.SMART, HerdGroup.Status.ACTIVE,
          new HerdGroup.Rules(HerdAnimalSex.FEMALE, HerdAnimalStatus.ACTIVE, 36, null, true, false), 0));
      assertThat(repository.animals(tenantId, farm, smart, LocalDate.of(2026, 9, 27), 0, 20)
          .items()).extracting(HerdAnimalSummary::id).containsExactly(female);
      execute(connection, "update app.animal_pregnancies set status='TERMINATED',ended_on='2026-09-27',termination_reason='OTHER' where id=?", pregnancy);
      assertThat(repository.animals(tenantId, farm, smart, LocalDate.of(2026, 9, 27), 0, 20)
          .items()).isEmpty();
    }
    try (Connection connection = apiConnection()) {
      setTenant(connection, otherTenant);
      JdbcHerdGroupRepository repository = repository(connection);
      assertThat(repository.find(tenantId, farm, manualId, false)).isEmpty();
      assertThat(repository.list(tenantId, farm, 20, 0)).isEmpty();
      assertThatThrownBy(() -> repository.insert(tenantId, farm, new HerdGroup(UUID.randomUUID(),
          "Grupo alheio", HerdGroup.Kind.MANUAL, HerdGroup.Status.ACTIVE,
          HerdGroup.Rules.empty(), 0))).isInstanceOf(DataAccessException.class);
      connection.rollback();
    }
  }

  private static JdbcHerdGroupRepository repository(Connection connection) {
    return new JdbcHerdGroupRepository(new NamedParameterJdbcTemplate(
        new SingleConnectionDataSource(connection, true)));
  }
}
