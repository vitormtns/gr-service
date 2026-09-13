package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdPlannerOperationRepository implements HerdPlannerOperationRepository {
  private final NamedParameterJdbcTemplate j;

  public JdbcHerdPlannerOperationRepository(NamedParameterJdbcTemplate j) {
    this.j = j;
  }

  private MapSqlParameterSource p(TenantId t, UUID f, UUID o) {
    return new MapSqlParameterSource()
        .addValue("tenant", t.value())
        .addValue("farm", f)
        .addValue("operation", o);
  }

  public void lock(TenantId t, UUID f, UUID o) {
    j.query(
        "select pg_advisory_xact_lock(hashtextextended(:key,0))",
        new MapSqlParameterSource("key", t.value() + ":" + f + ":" + o),
        r -> {});
  }

  public Optional<HerdPlannerOperation> find(TenantId t, UUID f, UUID o) {
    return j
        .query(
            "select * from app.herd_planner_operations where tenant_id=:tenant and farm_id=:farm"
                + " and operation_id=:operation",
            p(t, f, o),
            (r, n) ->
                new HerdPlannerOperation(
                    t,
                    f,
                    o,
                    HerdPlannerOperation.Type.valueOf(r.getString("operation_type")),
                    r.getObject("planner_item_id", UUID.class),
                    r.getString("command_payload"),
                    r.getLong("resulting_version"),
                    r.getObject("recorded_at", OffsetDateTime.class).toInstant()))
        .stream()
        .findFirst();
  }

  public void save(HerdPlannerOperation x) {
    j.update(
        "insert into"
            + " app.herd_planner_operations(tenant_id,farm_id,operation_id,operation_type,planner_item_id,command_payload,resulting_version)"
            + " values(:tenant,:farm,:operation,:type,:item,:payload,:version)",
        p(x.tenantId(), x.farmId(), x.operationId())
            .addValue("type", x.type().name())
            .addValue("item", x.plannerItemId())
            .addValue("payload", x.payload())
            .addValue("version", x.resultingVersion()));
  }
}
