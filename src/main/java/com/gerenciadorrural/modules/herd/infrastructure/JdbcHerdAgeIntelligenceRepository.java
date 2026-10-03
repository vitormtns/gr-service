package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdAgeIntelligenceRepository implements HerdAgeIntelligenceRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public JdbcHerdAgeIntelligenceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  // Seleção set-based. As fronteiras vêm da policy; o ajuste mantém meses completos
  // quando plusMonths cai em mês mais curto (ex.: 31/01 -> 01/05 aos três meses).
  private static final String CANDIDATES = """
      with boundaries as (select unnest(ARRAY[:boundaries]) months),
      candidates as (
        select a.id, a.identification, a.name, a.birth_date, min(d.transition_on) transition_on
        from app.animals a
        cross join boundaries b
        cross join lateral (select (a.birth_date + make_interval(months => b.months))::date candidate) c
        cross join lateral (select c.candidate +
          case when extract(day from c.candidate) < extract(day from a.birth_date)
          then 1 else 0 end transition_on) d
        where a.tenant_id=:tenantId and a.farm_id=:farmId and a.status='ACTIVE'
          and a.birth_date <= :referenceDate and d.transition_on > :referenceDate
        group by a.id, a.identification, a.name, a.birth_date
      )
      """;
  private static final String WINDOW = " where transition_on <= cast(:referenceDate as date) + :horizonDays";
  private MapSqlParameterSource parameters(TenantId tenant, UUID farm, LocalDate reference, int horizon) {
    return new MapSqlParameterSource("tenantId", tenant.value()).addValue("farmId", farm)
        .addValue("referenceDate", reference).addValue("horizonDays", horizon)
        .addValue("boundaries", AgePolicy.transitionBoundaries());
  }
  @Override public List<Animal> transitions(TenantId tenant, UUID farm, LocalDate reference,
      int horizon, int size, long offset) {
    return jdbc.query(CANDIDATES + "select * from candidates" + WINDOW
        + " order by transition_on, id limit :size offset :offset",
        parameters(tenant, farm, reference, horizon).addValue("size", size).addValue("offset", offset),
        (r, i) -> new Animal(r.getObject("id", UUID.class), r.getString("identification"),
            r.getString("name"), r.getObject("birth_date", LocalDate.class)));
  }
  @Override public long transitionCount(TenantId tenant, UUID farm, LocalDate reference, int horizon) {
    return jdbc.queryForObject(CANDIDATES + "select count(*) from candidates" + WINDOW,
        parameters(tenant, farm, reference, horizon), Long.class);
  }
}
