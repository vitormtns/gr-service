package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdPlannerRepository implements HerdPlannerRepository {
  private final NamedParameterJdbcTemplate j;

  public JdbcHerdPlannerRepository(NamedParameterJdbcTemplate j) {
    this.j = j;
  }

  private MapSqlParameterSource p(TenantId t, UUID f) {
    return new MapSqlParameterSource().addValue("tenant", t.value()).addValue("farm", f);
  }

  private HerdPlannerItem map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    return new HerdPlannerItem(
        r.getObject("id", UUID.class),
        new TenantId(r.getObject("tenant_id", UUID.class)),
        r.getObject("farm_id", UUID.class),
        r.getObject("animal_id", UUID.class),
        HerdPlannerType.valueOf(r.getString("type")),
        r.getString("title"),
        r.getString("notes"),
        r.getObject("scheduled_for", LocalDate.class),
        HerdPlannerStatus.valueOf(r.getString("status")),
        r.getLong("version"),
        r.getObject("created_by", UUID.class),
        r.getObject("created_at", OffsetDateTime.class).toInstant(),
        r.getObject("updated_at", OffsetDateTime.class).toInstant(),
        r.getObject("completed_at", OffsetDateTime.class) == null
            ? null
            : r.getObject("completed_at", OffsetDateTime.class).toInstant(),
        r.getObject("cancelled_at", OffsetDateTime.class) == null
            ? null
            : r.getObject("cancelled_at", OffsetDateTime.class).toInstant());
  }

  public Optional<HerdPlannerItem> find(TenantId t, UUID f, UUID id) {
    return j
        .query(
            "select * from app.herd_planner_items where tenant_id=:tenant and farm_id=:farm and"
                + " id=:id",
            p(t, f).addValue("id", id),
            this::map)
        .stream()
        .findFirst();
  }

  public HerdPlannerItem insert(HerdPlannerItem x) {
    return j.query(
            "insert into"
                + " app.herd_planner_items(id,tenant_id,farm_id,animal_id,type,title,notes,scheduled_for,created_by)"
                + " values(:id,:tenant,:farm,:animal,:type,:title,:notes,:date,:actor) returning *",
            p(x.tenantId(), x.farmId())
                .addValue("id", x.id())
                .addValue("animal", x.animalId())
                .addValue("type", x.type().name())
                .addValue("title", x.title())
                .addValue("notes", x.notes())
                .addValue("date", x.scheduledFor())
                .addValue("actor", x.createdBy()),
            this::map)
        .getFirst();
  }

  public Optional<HerdPlannerItem> update(
      TenantId t,
      UUID f,
      UUID id,
      long v,
      HerdPlannerType type,
      String title,
      String notes,
      LocalDate date,
      UUID animal) {
    return j
        .query(
            "update app.herd_planner_items set"
                + " type=:type,title=:title,notes=:notes,scheduled_for=:date,animal_id=:animal,version=version+1,updated_at=now()"
                + " where tenant_id=:tenant and farm_id=:farm and id=:id and status='OPEN' and"
                + " version=:v returning *",
            p(t, f)
                .addValue("id", id)
                .addValue("v", v)
                .addValue("type", type.name())
                .addValue("title", title)
                .addValue("notes", notes)
                .addValue("date", date)
                .addValue("animal", animal),
            this::map)
        .stream()
        .findFirst();
  }

  public Optional<HerdPlannerItem> transition(
      TenantId t, UUID f, UUID id, long v, HerdPlannerStatus s) {
    String column = s == HerdPlannerStatus.COMPLETED ? "completed_at" : "cancelled_at";
    return j
        .query(
            "update app.herd_planner_items set status=:status,"
                + column
                + "=now(),version=version+1,updated_at=now() where tenant_id=:tenant and"
                + " farm_id=:farm and id=:id and status='OPEN' and version=:v returning *",
            p(t, f).addValue("id", id).addValue("v", v).addValue("status", s.name()),
            this::map)
        .stream()
        .findFirst();
  }

  private MapSqlParameterSource filters(
      TenantId t,
      UUID f,
      HerdPlannerStatus s,
      HerdPlannerType type,
      UUID a,
      LocalDate from,
      LocalDate to) {
    return p(t, f)
        .addValue("status", s == null ? null : s.name())
        .addValue("type", type == null ? null : type.name())
        .addValue("animal", a)
        .addValue("from", from)
        .addValue("to", to);
  }

  private static final String WHERE =
      " where tenant_id=:tenant and farm_id=:farm and (cast(:status as text) is null or"
          + " status=:status) and (cast(:type as text) is null or type=:type) and (cast(:animal as"
          + " uuid) is null or animal_id=:animal) and (cast(:from as date) is null or"
          + " scheduled_for>=:from) and (cast(:to as date) is null or scheduled_for<=:to) ";

  public List<HerdPlannerItem> list(
      TenantId t,
      UUID f,
      HerdPlannerStatus s,
      HerdPlannerType type,
      UUID a,
      LocalDate from,
      LocalDate to,
      int limit,
      long offset) {
    return j.query(
        "select * from app.herd_planner_items"
            + WHERE
            + "order by scheduled_for,created_at,id limit :limit offset :offset",
        filters(t, f, s, type, a, from, to).addValue("limit", limit).addValue("offset", offset),
        this::map);
  }

  public long count(
      TenantId t,
      UUID f,
      HerdPlannerStatus s,
      HerdPlannerType type,
      UUID a,
      LocalDate from,
      LocalDate to) {
    Long x =
        j.queryForObject(
            "select count(*) from app.herd_planner_items" + WHERE,
            filters(t, f, s, type, a, from, to),
            Long.class);
    return x == null ? 0 : x;
  }
}
