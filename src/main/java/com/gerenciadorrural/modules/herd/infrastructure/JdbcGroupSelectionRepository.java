package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.GroupSelectionRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGroupSelectionRepository implements GroupSelectionRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public JdbcGroupSelectionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
  private static MapSqlParameterSource parameters(TenantId tenant, UUID farm) {
    return new MapSqlParameterSource("tenant", tenant.value()).addValue("farm", farm);
  }
  public void lockOperation(TenantId tenant, UUID farm, UUID operation) {
    jdbc.query("select pg_advisory_xact_lock(hashtextextended(:key,0))",
        new MapSqlParameterSource("key", "group:"+tenant.value()+":"+farm+":"+operation), r -> {});
  }
  public Optional<Receipt> receipt(TenantId tenant, UUID farm, UUID operation) {
    return jdbc.query("select command_payload,result_payload from app.herd_group_operations"
        + " where tenant_id=:tenant and farm_id=:farm and operation_id=:operation",
        parameters(tenant,farm).addValue("operation",operation),
        (r,i) -> new Receipt(r.getString(1),r.getString(2))).stream().findFirst();
  }
  public void saveReceipt(TenantId tenant, UUID farm, UUID operation, UUID group, String command, String result) {
    jdbc.update("insert into app.herd_group_operations(tenant_id,farm_id,operation_id,group_id,command_payload,result_payload)"
        + " values(:tenant,:farm,:operation,:group,:command,:result)", parameters(tenant,farm)
        .addValue("operation",operation).addValue("group",group).addValue("command",command).addValue("result",result));
  }
  public int lockSelectedAnimals(TenantId tenant, UUID farm, List<UUID> animals) {
    return jdbc.query("select id from app.animals where tenant_id=:tenant and farm_id=:farm"
        + " and id in (:animals) order by id for share", parameters(tenant,farm).addValue("animals",animals),
        (r,i) -> r.getObject(1,UUID.class)).size();
  }
  public int addMembers(TenantId tenant, UUID farm, UUID group, List<UUID> animals) {
    return jdbc.update("insert into app.herd_group_members(tenant_id,farm_id,group_id,animal_id)"
        + " select :tenant,:farm,:group,a.id from app.animals a where a.tenant_id=:tenant and a.farm_id=:farm"
        + " and a.id in (:animals) on conflict do nothing", parameters(tenant,farm)
        .addValue("group",group).addValue("animals",animals));
  }
}
