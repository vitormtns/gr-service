package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository;
import com.gerenciadorrural.modules.herd.domain.HerdReportCategory;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdDashboardRepository implements HerdDashboardRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcHerdDashboardRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public HerdSnapshot snapshot(
      TenantId tenant,
      UUID farm,
      HerdReportCategory category,
      HerdAnimalSex sex,
      UUID paddock) {
    MapSqlParameterSource parameters =
        base(tenant, farm)
            .addValue("sex", sex == null ? null : sex.name())
            .addValue("paddock", paddock);
    String where =
        """
         where a.tenant_id=:tenant and a.farm_id=:farm and a.status='ACTIVE'
           and (cast(:sex as text) is null or a.sex=:sex)
           and (cast(:paddock as uuid) is null or a.paddock_id=:paddock)
        """;
    SnapshotCounts counts =
        jdbc.queryForObject(
            """
            select count(*) total,
                   count(*) filter(where a.sex='MALE') male,
                   count(*) filter(where a.sex='FEMALE') female,
                   count(*) filter(where a.paddock_id is null) unlocated
              from app.animals a
            """
                + where,
            parameters,
            (result, row) ->
                new SnapshotCounts(
                    result.getLong("total"),
                    result.getLong("male"),
                    result.getLong("female"),
                    result.getLong("unlocated")));
    List<PaddockTotal> byPaddock =
        jdbc.query(
            """
            select p.id,p.name,count(*) total
              from app.animals a
              join app.paddocks p
                on p.tenant_id=a.tenant_id and p.farm_id=a.farm_id and p.id=a.paddock_id
            """
                + where
                + " group by p.id,p.name order by p.name,p.id",
            parameters,
            (result, row) ->
                new PaddockTotal(
                    result.getObject("id", UUID.class),
                    result.getString("name"),
                    result.getLong("total")));
    Map<HerdAnimalSex, Long> bySex = new EnumMap<>(HerdAnimalSex.class);
    bySex.put(HerdAnimalSex.MALE, counts.male());
    bySex.put(HerdAnimalSex.FEMALE, counts.female());
    Map<HerdReportCategory, Long> byCategory = new EnumMap<>(HerdReportCategory.class);
    byCategory.put(HerdReportCategory.UNCLASSIFIED, counts.total());
    return new HerdSnapshot(
        counts.total(), bySex, byCategory, byPaddock, counts.unlocated());
  }

  @Override
  public ActivityTotals activity(TenantId tenant, UUID farm, LocalDate from, LocalDate to) {
    return jdbc.queryForObject(
        """
        with lifecycle as (
          select count(*) filter(where event_type='BORN') births,
                 count(*) filter(where event_type='DECEASED') deaths,
                 count(*) filter(where event_type='SOLD') sales
            from app.animal_events
           where tenant_id=:tenant and farm_id=:farm
             and event_type in ('BORN','DECEASED','SOLD') and occurred_on between :from and :to
        ), movements as (
          select count(*) total
            from app.herd_movements m join app.animal_events e on e.id=m.event_id
           where m.tenant_id=:tenant and m.farm_id=:farm and e.event_type='MOVED'
             and e.occurred_on between :from and :to
        ), transfers as (
          select count(*) filter(where destination_farm_id=:farm) inbound,
                 count(*) filter(where source_farm_id=:farm) outbound
            from app.animal_transfers
           where tenant_id=:tenant and occurred_on between :from and :to
             and (source_farm_id=:farm or destination_farm_id=:farm)
        ), weights as (
          select count(*) total from app.animal_weight_measurements
           where tenant_id=:tenant and farm_id=:farm and measured_on between :from and :to
        ), health as (
          select count(*) filter(where treatment_type='VACCINATION') vaccinations,
                 count(*) filter(where treatment_type='DEWORMING') dewormings
            from app.animal_health_treatments
           where tenant_id=:tenant and farm_id=:farm and occurred_on between :from and :to
        ), reproduction as (
          select count(*) filter(where event_type='BREEDING_RECORDED') breedings,
                 count(*) filter(where event_type='PREGNANCY_CONFIRMED') confirmed,
                 count(*) filter(where event_type='PREGNANCY_ENDED') terminated,
                 count(*) filter(where event_type='CALVED') calvings,
                 count(*) filter(where event_type='BORN') calves
            from app.animal_events
           where tenant_id=:tenant and farm_id=:farm
             and event_type in ('BREEDING_RECORDED','PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN')
             and occurred_on between :from and :to
        ), planner as (
          select count(*) filter(where status='COMPLETED') completed,
                 count(*) filter(where status='CANCELLED') cancelled
            from app.herd_planner_items
           where tenant_id=:tenant and farm_id=:farm and scheduled_for between :from and :to
        )
        select lifecycle.*,movements.total movements,transfers.*,weights.total weights,
               health.*,reproduction.*,planner.*
          from lifecycle cross join movements cross join transfers cross join weights
          cross join health cross join reproduction cross join planner
        """,
        dated(tenant, farm, from, to),
        (result, row) ->
            new ActivityTotals(
                result.getLong("births"),
                result.getLong("deaths"),
                result.getLong("sales"),
                result.getLong("movements"),
                result.getLong("inbound"),
                result.getLong("outbound"),
                result.getLong("weights"),
                result.getLong("vaccinations"),
                result.getLong("dewormings"),
                result.getLong("breedings"),
                result.getLong("confirmed"),
                result.getLong("terminated"),
                result.getLong("calvings"),
                result.getLong("calves"),
                result.getLong("completed"),
                result.getLong("cancelled")));
  }

  @Override
  public List<ActivityBucket> activitySeries(
      TenantId tenant, UUID farm, LocalDate from, LocalDate to) {
    return jdbc.query(
        """
        with days as (
          select generate_series(cast(:from as date),cast(:to as date),interval '1 day')::date bucket_date
        ), facts as (
          select occurred_on bucket_date,event_type kind,count(*) total
            from app.animal_events
           where tenant_id=:tenant and farm_id=:farm
             and event_type in ('BORN','DECEASED','SOLD','BREEDING_RECORDED','CALVED')
             and occurred_on between :from and :to group by occurred_on,event_type
          union all
          select e.occurred_on,'MOVED',count(*)
            from app.herd_movements m join app.animal_events e on e.id=m.event_id
           where m.tenant_id=:tenant and m.farm_id=:farm and e.event_type='MOVED'
             and e.occurred_on between :from and :to group by e.occurred_on
          union all
          select measured_on,'WEIGHT',count(*) from app.animal_weight_measurements
           where tenant_id=:tenant and farm_id=:farm and measured_on between :from and :to
           group by measured_on
          union all
          select occurred_on,'HEALTH',count(*) from app.animal_health_treatments
           where tenant_id=:tenant and farm_id=:farm and occurred_on between :from and :to
           group by occurred_on
        ), totals as (
          select bucket_date,
                 coalesce(sum(total) filter(where kind='BORN'),0) births,
                 coalesce(sum(total) filter(where kind='DECEASED'),0) deaths,
                 coalesce(sum(total) filter(where kind='SOLD'),0) sales,
                 coalesce(sum(total) filter(where kind='MOVED'),0) movements,
                 coalesce(sum(total) filter(where kind='WEIGHT'),0) weights,
                 coalesce(sum(total) filter(where kind='HEALTH'),0) health,
                 coalesce(sum(total) filter(where kind='BREEDING_RECORDED'),0) breedings,
                 coalesce(sum(total) filter(where kind='CALVED'),0) calvings
            from facts group by bucket_date
        )
        select d.bucket_date,coalesce(t.births,0) births,coalesce(t.deaths,0) deaths,
               coalesce(t.sales,0) sales,coalesce(t.movements,0) movements,
               coalesce(t.weights,0) weights,coalesce(t.health,0) health,
               coalesce(t.breedings,0) breedings,coalesce(t.calvings,0) calvings
          from days d left join totals t on t.bucket_date=d.bucket_date order by d.bucket_date
        """,
        dated(tenant, farm, from, to),
        (result, row) ->
            new ActivityBucket(
                result.getObject("bucket_date", LocalDate.class),
                result.getLong("births"),
                result.getLong("deaths"),
                result.getLong("sales"),
                result.getLong("movements"),
                result.getLong("weights"),
                result.getLong("health"),
                result.getLong("breedings"),
                result.getLong("calvings")));
  }

  @Override
  public AttentionSummary attention(
      TenantId tenant,
      UUID farm,
      LocalDate reference,
      int weighingDueDays,
      int calvingUpcomingDays) {
    MapSqlParameterSource parameters =
        base(tenant, farm)
            .addValue("reference", reference)
            .addValue("cutoff", reference.minusDays(weighingDueDays))
            .addValue("upcoming", calvingUpcomingDays);
    return jdbc.queryForObject(
        JdbcHerdManagementRepository.PENDING_SQL
            + """
            , pending as (
              select count(*) filter(where type='VACCINATION_DUE') vaccination,
                     count(*) filter(where type='DEWORMING_DUE') deworming,
                     count(*) filter(where type='WEIGHING_DUE') weighing,
                     count(*) filter(where type='CALVING_UPCOMING') upcoming,
                     count(*) filter(where type='CALVING_OVERDUE') overdue
                from items
            ), planner as (
              select count(*) filter(where status='OPEN') open,
                     count(*) filter(where status='OPEN' and scheduled_for<:reference) overdue
                from app.herd_planner_items
               where tenant_id=:tenant and farm_id=:farm
            )
            select pending.*,planner.open planner_open,planner.overdue planner_overdue
              from pending cross join planner
            """,
        parameters,
        (result, row) ->
            new AttentionSummary(
                result.getLong("vaccination"),
                result.getLong("deworming"),
                result.getLong("weighing"),
                result.getLong("upcoming"),
                result.getLong("overdue"),
                result.getLong("planner_open"),
                result.getLong("planner_overdue")));
  }

  @Override
  public ReproductionPipeline reproductionPipeline(
      TenantId tenant, UUID farm, LocalDate reference, int calvingUpcomingDays) {
    return jdbc.queryForObject(
        """
        select count(*) filter(where p.status='POSSIBLE') possible,
               count(*) filter(where p.status='CONFIRMED') confirmed
          from app.animal_pregnancies p
          join app.animals a on a.tenant_id=p.tenant_id and a.id=p.mother_animal_id
         where p.tenant_id=:tenant and a.farm_id=:farm and a.status='ACTIVE'
           and p.status in ('POSSIBLE','CONFIRMED')
        """,
        base(tenant, farm),
        (result, row) ->
            new ReproductionPipeline(result.getLong("possible"), result.getLong("confirmed")));
  }

  private static MapSqlParameterSource base(TenantId tenant, UUID farm) {
    return new MapSqlParameterSource().addValue("tenant", tenant.value()).addValue("farm", farm);
  }

  private static MapSqlParameterSource dated(
      TenantId tenant, UUID farm, LocalDate from, LocalDate to) {
    return base(tenant, farm).addValue("from", from).addValue("to", to);
  }

  private record SnapshotCounts(long total, long male, long female, long unlocated) {}
}
