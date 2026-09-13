package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.domain.HerdReportRepository.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdReportRepository implements HerdReportRepository {
  private static final String HISTORICAL_SNAPSHOTS =
      """
      with snapshots as (
        select distinct on (animal_id) animal_id,
               payload->>'identification' identification, payload->>'name' name
          from app.animal_events
         where tenant_id=:tenant and farm_id=:farm and event_type='CREATED'
         order by animal_id,recorded_at,id
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcHerdReportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ReportPage<HerdPositionSummary, HerdPositionItem> herdPosition(
      TenantId tenant,
      UUID farm,
      HerdReportCategory category,
      HerdAnimalSex sex,
      UUID paddock,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        base(tenant, farm)
            .addValue("sex", sex == null ? null : sex.name())
            .addValue("paddock", paddock)
            .addValue("limit", limit)
            .addValue("offset", offset);
    String where =
        """
         where a.tenant_id=:tenant and a.farm_id=:farm and a.status='ACTIVE'
           and (cast(:sex as text) is null or a.sex=:sex)
           and (cast(:paddock as uuid) is null or a.paddock_id=:paddock)
        """;
    long total = number("select count(*) from app.animals a" + where, p);
    Map<HerdAnimalSex, Long> bySex = new EnumMap<>(HerdAnimalSex.class);
    for (HerdAnimalSex value : HerdAnimalSex.values()) {
      bySex.put(value, 0L);
    }
    jdbc.query(
        "select a.sex,count(*) total from app.animals a" + where + " group by a.sex order by a.sex",
        p,
        (RowCallbackHandler)
            rs -> bySex.put(HerdAnimalSex.valueOf(rs.getString("sex")), rs.getLong("total")));
    List<PaddockTotal> byPaddock =
        jdbc.query(
            """
            select p.id,p.name,count(*) total from app.animals a
              join app.paddocks p on p.tenant_id=a.tenant_id and p.farm_id=a.farm_id and p.id=a.paddock_id
            """
                + where
                + " group by p.id,p.name order by p.name,p.id",
            p,
            (rs, n) ->
                new PaddockTotal(
                    new PaddockReference(rs.getObject("id", UUID.class), rs.getString("name")),
                    rs.getLong("total")));
    long unlocated =
        number("select count(*) from app.animals a" + where + " and a.paddock_id is null", p);
    List<HerdPositionItem> items =
        jdbc.query(
            """
            select a.id,a.identification,a.name,a.sex,a.birth_date,pd.id paddock_id,pd.name paddock_name
              from app.animals a
              left join app.paddocks pd on pd.tenant_id=a.tenant_id and pd.farm_id=a.farm_id and pd.id=a.paddock_id
            """
                + where
                + " order by lower(btrim(a.identification)),a.id limit :limit offset :offset",
            p,
            (rs, n) ->
                new HerdPositionItem(
                    animal(rs, "id", "identification", "name"),
                    HerdReportCategory.UNCLASSIFIED,
                    HerdAnimalSex.valueOf(rs.getString("sex")),
                    rs.getObject("birth_date", LocalDate.class),
                    paddock(rs, "paddock_id", "paddock_name")));
    Map<HerdReportCategory, Long> byCategory = new EnumMap<>(HerdReportCategory.class);
    byCategory.put(HerdReportCategory.UNCLASSIFIED, total);
    return new ReportPage<>(
        new HerdPositionSummary(total, byCategory, bySex, byPaddock, unlocated), items, total);
  }

  @Override
  public ReportPage<LifecycleSummary, LifecycleItem> lifecycle(
      TenantId tenant,
      UUID farm,
      LocalDate from,
      LocalDate to,
      LifecycleEvent event,
      UUID animal,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        dated(tenant, farm, from, to)
            .addValue("event", event == null ? null : event.name())
            .addValue("animal", animal)
            .addValue("limit", limit)
            .addValue("offset", offset);
    String where =
        """
         where e.tenant_id=:tenant and e.farm_id=:farm
           and e.event_type in ('CREATED','BORN','SOLD','DECEASED')
           and coalesce(e.occurred_on,e.recorded_at::date) between :from and :to
           and (cast(:event as text) is null or e.event_type=:event)
           and (cast(:animal as uuid) is null or e.animal_id=:animal)
        """;
    Map<LifecycleEvent, Long> counts = new EnumMap<>(LifecycleEvent.class);
    for (LifecycleEvent value : LifecycleEvent.values()) {
      counts.put(value, 0L);
    }
    jdbc.query(
        "select e.event_type,count(*) total from app.animal_events e"
            + where
            + " group by e.event_type order by e.event_type",
        p,
        (RowCallbackHandler)
            rs ->
                counts.put(
                    LifecycleEvent.valueOf(rs.getString("event_type")), rs.getLong("total")));
    long affected =
        number("select count(distinct e.animal_id) from app.animal_events e" + where, p);
    long total = number("select count(*) from app.animal_events e" + where, p);
    List<LifecycleItem> items =
        jdbc.query(
            HISTORICAL_SNAPSHOTS
                + """
                select e.id,e.animal_id,s.identification,s.name,e.event_type,
                       coalesce(e.occurred_on,e.recorded_at::date) effective_on,e.recorded_at,
                       e.payload->>'notes' notes
                  from app.animal_events e left join snapshots s on s.animal_id=e.animal_id
                """
                + where
                + " order by coalesce(e.occurred_on,e.recorded_at::date) desc,e.recorded_at desc,e.id desc limit :limit offset :offset",
            p,
            (rs, n) ->
                new LifecycleItem(
                    rs.getObject("id", UUID.class),
                    animal(rs, "animal_id", "identification", "name"),
                    LifecycleEvent.valueOf(rs.getString("event_type")),
                    rs.getObject("effective_on", LocalDate.class),
                    instant(rs, "recorded_at"),
                    rs.getString("notes")));
    return new ReportPage<>(new LifecycleSummary(counts, affected), items, total);
  }

  @Override
  public ReportPage<MovementSummary, MovementItem> movements(
      TenantId tenant,
      UUID farm,
      LocalDate from,
      LocalDate to,
      UUID animal,
      UUID source,
      UUID destination,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        dated(tenant, farm, from, to)
            .addValue("animal", animal)
            .addValue("source", source)
            .addValue("destination", destination)
            .addValue("limit", limit)
            .addValue("offset", offset);
    String joins =
        """
         from app.herd_movements m join app.animal_events e on e.id=m.event_id
        """;
    String where =
        """
         where m.tenant_id=:tenant and m.farm_id=:farm and e.event_type='MOVED'
           and e.occurred_on between :from and :to
           and (cast(:animal as uuid) is null or m.animal_id=:animal)
           and (cast(:source as uuid) is null or m.source_paddock_id=:source)
           and (cast(:destination as uuid) is null or m.destination_paddock_id=:destination)
        """;
    long total = number("select count(*)" + joins + where, p);
    long distinct = number("select count(distinct m.animal_id)" + joins + where, p);
    List<MovementItem> items =
        jdbc.query(
            HISTORICAL_SNAPSHOTS
                + """
                select e.id,m.animal_id,s.identification,s.name,e.occurred_on,e.recorded_at,
                       m.source_paddock_id,e.payload->>'sourcePaddockName' source_name,
                       m.destination_paddock_id,e.payload->>'destinationPaddockName' destination_name,
                       e.payload->>'notes' notes
                """
                + joins
                + " left join snapshots s on s.animal_id=m.animal_id"
                + where
                + " order by e.occurred_on desc,e.recorded_at desc,e.id desc limit :limit offset :offset",
            p,
            (rs, n) ->
                new MovementItem(
                    rs.getObject("id", UUID.class),
                    animal(rs, "animal_id", "identification", "name"),
                    rs.getObject("occurred_on", LocalDate.class),
                    instant(rs, "recorded_at"),
                    paddock(rs, "source_paddock_id", "source_name"),
                    paddock(rs, "destination_paddock_id", "destination_name"),
                    rs.getString("notes")));
    return new ReportPage<>(new MovementSummary(total, distinct), items, total);
  }

  @Override
  public ReportPage<TransferSummary, TransferItem> transfers(
      TenantId tenant,
      UUID farm,
      LocalDate from,
      LocalDate to,
      TransferDirection direction,
      UUID animal,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        dated(tenant, farm, from, to)
            .addValue("direction", direction.name())
            .addValue("animal", animal)
            .addValue("limit", limit)
            .addValue("offset", offset);
    String where =
        """
         where t.tenant_id=:tenant and t.occurred_on between :from and :to
           and ((:direction='IN' and t.destination_farm_id=:farm)
             or (:direction='OUT' and t.source_farm_id=:farm)
             or (:direction='ALL' and (t.source_farm_id=:farm or t.destination_farm_id=:farm)))
           and (cast(:animal as uuid) is null or t.animal_id=:animal)
        """;
    long total = number("select count(*) from app.animal_transfers t" + where, p);
    long inbound =
        number(
            "select count(*) from app.animal_transfers t" + where + " and t.destination_farm_id=:farm",
            p);
    long outbound =
        number(
            "select count(*) from app.animal_transfers t" + where + " and t.source_farm_id=:farm", p);
    long distinct =
        number("select count(distinct t.animal_id) from app.animal_transfers t" + where, p);
    List<TransferItem> items =
        jdbc.query(
            HISTORICAL_SNAPSHOTS
                + """
                select t.*,s.identification,s.name
                  from app.animal_transfers t left join snapshots s on s.animal_id=t.animal_id
                """
                + where
                + " order by t.occurred_on desc,t.recorded_at desc,t.id desc limit :limit offset :offset",
            p,
            (rs, n) -> transfer(rs, farm));
    return new ReportPage<>(
        new TransferSummary(total, inbound, outbound, distinct), items, total);
  }

  @Override
  public ReportPage<WeightSummary, WeightItem> weights(
      TenantId tenant,
      UUID farm,
      LocalDate from,
      LocalDate to,
      UUID animal,
      HerdReportCategory category,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        dated(tenant, farm, from, to)
            .addValue("animal", animal)
            .addValue("limit", limit)
            .addValue("offset", offset);
    String where =
        " where w.tenant_id=:tenant and w.farm_id=:farm and w.measured_on between :from and :to"
            + " and (cast(:animal as uuid) is null or w.animal_id=:animal)";
    WeightSummary summary =
        jdbc.queryForObject(
            """
            select count(*) measurements,count(distinct w.animal_id) animals,
                   avg(w.weight_kg) average,min(w.weight_kg) minimum,max(w.weight_kg) maximum
              from app.animal_weight_measurements w
            """
                + where,
            p,
            (rs, n) ->
                new WeightSummary(
                    rs.getLong("measurements"),
                    rs.getLong("animals"),
                    rs.getBigDecimal("average"),
                    rs.getBigDecimal("minimum"),
                    rs.getBigDecimal("maximum")));
    List<WeightItem> items =
        jdbc.query(
            HISTORICAL_SNAPSHOTS
                + """
                select w.id,w.animal_id,s.identification,s.name,w.measured_on,w.recorded_at,w.weight_kg
                  from app.animal_weight_measurements w left join snapshots s on s.animal_id=w.animal_id
                """
                + where
                + " order by w.measured_on desc,w.recorded_at desc,w.id desc limit :limit offset :offset",
            p,
            (rs, n) ->
                new WeightItem(
                    rs.getObject("id", UUID.class),
                    animal(rs, "animal_id", "identification", "name"),
                    rs.getObject("measured_on", LocalDate.class),
                    instant(rs, "recorded_at"),
                    rs.getBigDecimal("weight_kg")));
    return new ReportPage<>(summary, items, summary.measurementCount());
  }

  @Override
  public ReportPage<HealthSummary, HealthItem> health(
      TenantId tenant,
      UUID farm,
      LocalDate from,
      LocalDate to,
      HealthTreatmentType type,
      UUID animal,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        dated(tenant, farm, from, to)
            .addValue("type", type == null ? null : type.name())
            .addValue("animal", animal)
            .addValue("limit", limit)
            .addValue("offset", offset);
    String where =
        """
         where h.tenant_id=:tenant and h.farm_id=:farm and h.occurred_on between :from and :to
           and (cast(:type as text) is null or h.treatment_type=:type)
           and (cast(:animal as uuid) is null or h.animal_id=:animal)
        """;
    long total = number("select count(*) from app.animal_health_treatments h" + where, p);
    long animals =
        number("select count(distinct h.animal_id) from app.animal_health_treatments h" + where, p);
    Map<HealthTreatmentType, Long> counts = new EnumMap<>(HealthTreatmentType.class);
    for (HealthTreatmentType value : HealthTreatmentType.values()) {
      counts.put(value, 0L);
    }
    jdbc.query(
        "select h.treatment_type,count(*) total from app.animal_health_treatments h"
            + where
            + " group by h.treatment_type order by h.treatment_type",
        p,
        (RowCallbackHandler)
            rs ->
                counts.put(
                    HealthTreatmentType.valueOf(rs.getString("treatment_type")), rs.getLong("total")));
    List<HealthItem> items =
        jdbc.query(
            HISTORICAL_SNAPSHOTS
                + """
                select h.id,h.animal_id,s.identification,s.name,h.treatment_type,h.occurred_on,
                       h.recorded_at,h.product,h.protocol,h.next_due_on
                  from app.animal_health_treatments h left join snapshots s on s.animal_id=h.animal_id
                """
                + where
                + " order by h.occurred_on desc,h.recorded_at desc,h.id desc limit :limit offset :offset",
            p,
            (rs, n) ->
                new HealthItem(
                    rs.getObject("id", UUID.class),
                    animal(rs, "animal_id", "identification", "name"),
                    HealthTreatmentType.valueOf(rs.getString("treatment_type")),
                    rs.getObject("occurred_on", LocalDate.class),
                    instant(rs, "recorded_at"),
                    rs.getString("product"),
                    rs.getString("protocol"),
                    rs.getObject("next_due_on", LocalDate.class)));
    return new ReportPage<>(new HealthSummary(total, animals, counts), items, total);
  }

  @Override
  public ReportPage<ReproductionSummary, ReproductionItem> reproduction(
      TenantId tenant,
      UUID farm,
      LocalDate from,
      LocalDate to,
      UUID mother,
      ReproductionServiceType serviceType,
      PregnancyStatus pregnancyStatus,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        dated(tenant, farm, from, to)
            .addValue("mother", mother)
            .addValue("service", serviceType == null ? null : serviceType.name())
            .addValue("pregnancyStatus", pregnancyStatus == null ? null : pregnancyStatus.name())
            .addValue("limit", limit)
            .addValue("offset", offset);
    String joins =
        """
         from app.animal_events e
         left join app.animal_pregnancies p on p.tenant_id=e.tenant_id
           and p.id=cast(nullif(e.payload->>'pregnancyId','') as uuid)
        """;
    String where =
        """
         where e.tenant_id=:tenant and e.farm_id=:farm
           and e.event_type in ('BREEDING_RECORDED','PREGNANCY_CONFIRMED','PREGNANCY_ENDED','CALVED','BORN')
           and e.occurred_on between :from and :to
           and (cast(:mother as uuid) is null or
                case when e.event_type='BORN' then cast(e.payload->>'motherAnimalId' as uuid)
                     else e.animal_id end=:mother)
           and (cast(:service as text) is null or coalesce(e.payload->>'serviceType',p.service_type)=:service)
           and (cast(:pregnancyStatus as text) is null or p.status=:pregnancyStatus)
        """;
    Map<ReproductionAction, Long> counts = new EnumMap<>(ReproductionAction.class);
    jdbc.query(
        "select e.event_type,count(*) total" + joins + where + " group by e.event_type",
        p,
        (RowCallbackHandler)
            rs -> counts.put(action(rs.getString("event_type")), rs.getLong("total")));
    long openPossible =
        pregnancyStatus == null || pregnancyStatus == PregnancyStatus.POSSIBLE
            ? openPregnancies(
                tenant, farm, PregnancyStatus.POSSIBLE, mother, serviceType, from, to)
            : 0;
    long openConfirmed =
        pregnancyStatus == null || pregnancyStatus == PregnancyStatus.CONFIRMED
            ? openPregnancies(
                tenant, farm, PregnancyStatus.CONFIRMED, mother, serviceType, from, to)
            : 0;
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    ReproductionSummary summary =
        new ReproductionSummary(
            counts.getOrDefault(ReproductionAction.BREEDING_RECORDED, 0L),
            counts.getOrDefault(ReproductionAction.PREGNANCY_CONFIRMED, 0L),
            counts.getOrDefault(ReproductionAction.PREGNANCY_TERMINATED, 0L),
            counts.getOrDefault(ReproductionAction.CALVED, 0L),
            counts.getOrDefault(ReproductionAction.BORN, 0L),
            openPossible,
            openConfirmed);
    List<ReproductionItem> items =
        jdbc.query(
            HISTORICAL_SNAPSHOTS
                + """
                select e.id,
                       case when e.event_type='BORN' then cast(e.payload->>'motherAnimalId' as uuid)
                            else e.animal_id end mother_id,
                       s.identification,s.name,e.event_type,e.occurred_on,e.recorded_at,
                       cast(nullif(e.payload->>'pregnancyId','') as uuid) pregnancy_id,
                       coalesce(e.payload->>'serviceType',p.service_type) service_type,
                       coalesce(cast(nullif(e.payload->>'expectedCalvingOn','') as date),p.expected_calving_on) expected_calving_on,
                       case when e.event_type='BORN' then e.animal_id
                            else cast(nullif(e.payload->>'calfAnimalId','') as uuid) end calf_id,
                       e.payload->>'terminationReason' termination_reason
                """
                + joins
                + " left join snapshots s on s.animal_id=case when e.event_type='BORN' then cast(e.payload->>'motherAnimalId' as uuid) else e.animal_id end"
                + where
                + " order by e.occurred_on desc,e.recorded_at desc,e.id desc limit :limit offset :offset",
            p,
            this::reproduction);
    return new ReportPage<>(summary, items, total);
  }

  @Override
  public ReportPage<PlannerSummary, PlannerItem> planner(
      TenantId tenant,
      UUID farm,
      LocalDate from,
      LocalDate to,
      LocalDate reference,
      HerdPlannerStatus status,
      HerdPlannerType type,
      UUID animal,
      int limit,
      long offset) {
    MapSqlParameterSource p =
        dated(tenant, farm, from, to)
            .addValue("reference", reference)
            .addValue("status", status == null ? null : status.name())
            .addValue("type", type == null ? null : type.name())
            .addValue("animal", animal)
            .addValue("limit", limit)
            .addValue("offset", offset);
    String where =
        """
         where i.tenant_id=:tenant and i.farm_id=:farm and i.scheduled_for between :from and :to
           and (cast(:status as text) is null or i.status=:status)
           and (cast(:type as text) is null or i.type=:type)
           and (cast(:animal as uuid) is null or i.animal_id=:animal)
        """;
    PlannerSummary summary =
        jdbc.queryForObject(
            """
            select count(*) filter(where i.status='OPEN') open,
                   count(*) filter(where i.status='COMPLETED') completed,
                   count(*) filter(where i.status='CANCELLED') cancelled,
                   count(*) filter(where i.status='OPEN' and i.scheduled_for<:reference) overdue
              from app.herd_planner_items i
            """
                + where,
            p,
            (rs, n) ->
                new PlannerSummary(
                    rs.getLong("open"),
                    rs.getLong("completed"),
                    rs.getLong("cancelled"),
                    rs.getLong("overdue")));
    long total = summary.open() + summary.completed() + summary.cancelled();
    List<PlannerItem> items =
        jdbc.query(
            HISTORICAL_SNAPSHOTS
                + """
                select i.*,s.identification,s.name
                  from app.herd_planner_items i left join snapshots s on s.animal_id=i.animal_id
                """
                + where
                + " order by i.scheduled_for desc,i.created_at desc,i.id desc limit :limit offset :offset",
            p,
            (rs, n) ->
                new PlannerItem(
                    rs.getObject("id", UUID.class),
                    HerdPlannerType.valueOf(rs.getString("type")),
                    rs.getString("title"),
                    rs.getString("notes"),
                    rs.getObject("scheduled_for", LocalDate.class),
                    HerdPlannerStatus.valueOf(rs.getString("status")),
                    rs.getObject("animal_id", UUID.class) == null
                        ? null
                        : animal(rs, "animal_id", "identification", "name"),
                    rs.getLong("version"),
                    instant(rs, "created_at"),
                    instant(rs, "updated_at")));
    return new ReportPage<>(summary, items, total);
  }

  private long openPregnancies(
      TenantId tenant,
      UUID farm,
      PregnancyStatus status,
      UUID mother,
      ReproductionServiceType serviceType,
      LocalDate from,
      LocalDate to) {
    return number(
        """
        select count(*) from app.animal_pregnancies p
          join app.animals a on a.tenant_id=p.tenant_id and a.id=p.mother_animal_id
         where p.tenant_id=:tenant and a.farm_id=:farm and p.status=:status
           and p.service_on between :from and :to
           and (cast(:mother as uuid) is null or p.mother_animal_id=:mother)
           and (cast(:service as text) is null or p.service_type=:service)
        """,
        base(tenant, farm)
            .addValue("status", status.name())
            .addValue("mother", mother)
            .addValue("service", serviceType == null ? null : serviceType.name())
            .addValue("from", from)
            .addValue("to", to));
  }

  private TransferItem transfer(ResultSet rs, UUID currentFarm) throws SQLException {
    UUID sourceId = rs.getObject("source_farm_id", UUID.class);
    UUID destinationId = rs.getObject("destination_farm_id", UUID.class);
    boolean inbound = destinationId.equals(currentFarm);
    FarmReference source =
        sourceId.equals(currentFarm)
            ? new FarmReference(sourceId, rs.getString("source_farm_name"))
            : null;
    FarmReference destination =
        destinationId.equals(currentFarm)
            ? new FarmReference(destinationId, rs.getString("destination_farm_name"))
            : null;
    return new TransferItem(
        rs.getObject("id", UUID.class),
        animal(rs, "animal_id", "identification", "name"),
        inbound ? TransferDirection.IN : TransferDirection.OUT,
        rs.getObject("occurred_on", LocalDate.class),
        instant(rs, "recorded_at"),
        source,
        destination,
        destination == null
            ? null
            : paddock(rs, "destination_paddock_id", "destination_paddock_name"),
        rs.getString("notes"));
  }

  private ReproductionItem reproduction(ResultSet rs, int row) throws SQLException {
    String service = rs.getString("service_type");
    String reason = rs.getString("termination_reason");
    return new ReproductionItem(
        rs.getObject("id", UUID.class),
        animal(rs, "mother_id", "identification", "name"),
        action(rs.getString("event_type")),
        rs.getObject("occurred_on", LocalDate.class),
        instant(rs, "recorded_at"),
        rs.getObject("pregnancy_id", UUID.class),
        service == null ? null : ReproductionServiceType.valueOf(service),
        rs.getObject("expected_calving_on", LocalDate.class),
        rs.getObject("calf_id", UUID.class),
        reason == null ? null : PregnancyTerminationReason.valueOf(reason));
  }

  private static ReproductionAction action(String event) {
    return "PREGNANCY_ENDED".equals(event)
        ? ReproductionAction.PREGNANCY_TERMINATED
        : ReproductionAction.valueOf(event);
  }

  private long number(String sql, MapSqlParameterSource parameters) {
    Long value = jdbc.queryForObject(sql, parameters, Long.class);
    return value == null ? 0 : value;
  }

  private static MapSqlParameterSource base(TenantId tenant, UUID farm) {
    return new MapSqlParameterSource()
        .addValue("tenant", tenant.value())
        .addValue("farm", farm);
  }

  private static MapSqlParameterSource dated(
      TenantId tenant, UUID farm, LocalDate from, LocalDate to) {
    return base(tenant, farm).addValue("from", from).addValue("to", to);
  }

  private static AnimalReference animal(
      ResultSet rs, String id, String identification, String name) throws SQLException {
    return new AnimalReference(
        rs.getObject(id, UUID.class), rs.getString(identification), rs.getString(name));
  }

  private static PaddockReference paddock(ResultSet rs, String id, String name)
      throws SQLException {
    UUID value = rs.getObject(id, UUID.class);
    return value == null ? null : new PaddockReference(value, rs.getString(name));
  }

  private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }
}
