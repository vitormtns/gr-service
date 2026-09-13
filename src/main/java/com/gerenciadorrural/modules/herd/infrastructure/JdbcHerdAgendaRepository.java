package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdAgendaRepository implements HerdAgendaRepository {
  private final NamedParameterJdbcTemplate j;

  public JdbcHerdAgendaRepository(NamedParameterJdbcTemplate j) {
    this.j = j;
  }

  private MapSqlParameterSource p(
      TenantId t,
      UUID f,
      LocalDate r,
      int wd,
      int ud,
      HerdAgendaSource s,
      HerdPlannerType type,
      UUID a,
      LocalDate from,
      LocalDate to) {
    return new MapSqlParameterSource()
        .addValue("tenant", t.value())
        .addValue("farm", f)
        .addValue("reference", r)
        .addValue("cutoff", r.minusDays(wd))
        .addValue("upcoming", ud)
        .addValue("source", s == null ? null : s.name())
        .addValue("kind", type == null ? null : type.name())
        .addValue("animal", a)
        .addValue("from", from)
        .addValue("to", to);
  }

  private static final String SQL =
      JdbcHerdManagementRepository.PENDING_SQL
          + """
, manual_items as (
    select 'MANUAL' source,type kind,scheduled_for operational_date,id::text stable_id,animal_id,title summary,
           null::text identification,null::text name,id planner_item_id,null::text pending_type,
           null::uuid pregnancy_id,status
      from app.herd_planner_items
     where tenant_id=:tenant and farm_id=:farm and status='OPEN'
  ),
derived_items as (
    select 'DERIVED' source,
           case type when 'VACCINATION_DUE' then 'VACCINATION' when 'DEWORMING_DUE' then 'DEWORMING'
                     when 'WEIGHING_DUE' then 'WEIGHING' else 'CALVING' end kind,
           due_on operational_date,coalesce(pregnancy_id::text,animal_id::text||':'||type) stable_id,
           animal_id,
           case type when 'VACCINATION_DUE' then 'Vacinação pendente'
                     when 'DEWORMING_DUE' then 'Vermifugação pendente'
                     when 'WEIGHING_DUE' then 'Pesagem pendente'
                     when 'CALVING_UPCOMING' then 'Parto próximo'
                     else 'Parto atrasado' end summary,
           identification,name,null::uuid planner_item_id,type pending_type,pregnancy_id,null::text status
      from items
  ),
agenda as (select * from manual_items union all select * from derived_items)
select * from agenda where (cast(:source as text) is null or source=:source) and (cast(:kind as text) is null or kind=:kind) and (cast(:animal as uuid) is null or animal_id=:animal) and (cast(:from as date) is null or operational_date>=:from) and (cast(:to as date) is null or operational_date<=:to)
""";

  private RowMapper<Row> mapper() {
    return (r, n) ->
        new Row(
            HerdAgendaSource.valueOf(r.getString("source")),
            r.getString("kind"),
            r.getObject("operational_date", LocalDate.class),
            r.getString("stable_id"),
            r.getObject("animal_id", UUID.class),
            r.getString("summary"),
            r.getString("identification"),
            r.getString("name"),
            r.getObject("planner_item_id", UUID.class),
            r.getString("pending_type") == null
                ? null
                : PendingWorkType.valueOf(r.getString("pending_type")),
            r.getObject("pregnancy_id", UUID.class),
            r.getString("status") == null
                ? null
                : HerdPlannerStatus.valueOf(r.getString("status")));
  }

  public List<Row> page(
      TenantId t,
      UUID f,
      LocalDate r,
      int wd,
      int ud,
      HerdAgendaSource s,
      HerdPlannerType type,
      UUID a,
      LocalDate from,
      LocalDate to,
      int size,
      long offset) {
    return j.query(
        SQL + " order by operational_date,source,kind,stable_id limit :size offset :offset",
        p(t, f, r, wd, ud, s, type, a, from, to).addValue("size", size).addValue("offset", offset),
        mapper());
  }

  public long count(
      TenantId t,
      UUID f,
      LocalDate r,
      int wd,
      int ud,
      HerdAgendaSource s,
      HerdPlannerType type,
      UUID a,
      LocalDate from,
      LocalDate to) {
    Long n =
        j.queryForObject(
            "select count(*) from (" + SQL + ") x",
            p(t, f, r, wd, ud, s, type, a, from, to),
            Long.class);
    return n == null ? 0 : n;
  }
}
