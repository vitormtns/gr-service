package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.MilkRecordRepository;
import com.gerenciadorrural.modules.herd.domain.MilkSession;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMilkRecordRepository implements MilkRecordRepository {
  private static final String COLUMNS =
      "id,operation_id,animal_id,recorded_on,liters,session,notes,actor_user_id,recorded_at";
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcMilkRecordRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public MilkRecord insert(TenantId tenant, UUID farm, UUID animal, UUID operation,
      LocalDate day, BigDecimal liters, MilkSession session, String notes, UUID actor) {
    UUID id = UUID.randomUUID();
    var params = parameters(tenant, farm, animal).addValue("id", id)
        .addValue("operation", operation).addValue("day", day).addValue("liters", liters)
        .addValue("session", session == null ? null : session.name())
        .addValue("notes", notes).addValue("actor", actor);
    return jdbc.queryForObject("""
        insert into app.animal_milk_records
          (id,tenant_id,farm_id,animal_id,operation_id,recorded_on,liters,session,notes,actor_user_id)
        values (:id,:tenant,:farm,:animal,:operation,:day,:liters,:session,:notes,:actor)
        returning
        """ + COLUMNS, params, JdbcMilkRecordRepository::map);
  }

  @Override
  public Optional<MilkRecord> byOperation(TenantId tenant, UUID farm, UUID operation) {
    return jdbc.query("select " + COLUMNS + " from app.animal_milk_records"
        + " where tenant_id=:tenant and farm_id=:farm and operation_id=:operation",
        parameters(tenant, farm, null).addValue("operation", operation),
        JdbcMilkRecordRepository::map).stream().findFirst();
  }

  @Override
  public List<MilkRecord> history(TenantId tenant, UUID farm, UUID animal,
      int size, long offset) {
    return jdbc.query("select " + COLUMNS + " from app.animal_milk_records"
        + " where tenant_id=:tenant and farm_id=:farm and animal_id=:animal"
        + " order by recorded_on desc,recorded_at desc,id desc limit :size offset :offset",
        parameters(tenant, farm, animal).addValue("size", size).addValue("offset", offset),
        JdbcMilkRecordRepository::map);
  }

  @Override
  public long count(TenantId tenant, UUID farm, UUID animal) {
    return jdbc.queryForObject("select count(*) from app.animal_milk_records"
        + " where tenant_id=:tenant and farm_id=:farm and animal_id=:animal",
        parameters(tenant, farm, animal), Long.class);
  }

  @Override
  public MilkOverview overview(TenantId tenant, UUID farm, LocalDate reference) {
    return jdbc.queryForObject("""
        select coalesce(sum(liters) filter (where recorded_on=:reference),0) liters_today,
               count(distinct animal_id) filter (where recorded_on=:reference) females_today,
               avg(liters) filter (where recorded_on between :start and :reference) average_seven
          from app.animal_milk_records
         where tenant_id=:tenant and farm_id=:farm
           and recorded_on between :start and :reference
        """, parameters(tenant, farm, null).addValue("reference", reference)
        .addValue("start", reference.minusDays(6)),
        (rs, row) -> new MilkOverview(rs.getBigDecimal("liters_today"),
            rs.getLong("females_today"), rs.getBigDecimal("average_seven")));
  }

  @Override
  public MilkAnimalStats animalStats(TenantId tenant, UUID farm, UUID animal,
      LocalDate reference) {
    var params = parameters(tenant, farm, animal).addValue("reference", reference)
        .addValue("start", reference.minusDays(6));
    MilkRecord last = jdbc.query("select " + COLUMNS + " from app.animal_milk_records"
        + " where tenant_id=:tenant and farm_id=:farm and animal_id=:animal"
        + " and recorded_on<=:reference"
        + " order by recorded_on desc,recorded_at desc,id desc limit 1", params,
        JdbcMilkRecordRepository::map).stream().findFirst().orElse(null);
    return jdbc.queryForObject("""
        select count(*) records, avg(liters) average
          from app.animal_milk_records
         where tenant_id=:tenant and farm_id=:farm and animal_id=:animal
           and recorded_on between :start and :reference
        """, params, (rs, row) -> new MilkAnimalStats(last,
        rs.getBigDecimal("average"), rs.getLong("records")));
  }

  private static MapSqlParameterSource parameters(TenantId tenant, UUID farm, UUID animal) {
    return new MapSqlParameterSource().addValue("tenant", tenant.value())
        .addValue("farm", farm).addValue("animal", animal);
  }

  private static MilkRecord map(ResultSet rs, int row) throws SQLException {
    String session = rs.getString("session");
    return new MilkRecord(rs.getObject("id", UUID.class),
        rs.getObject("operation_id", UUID.class), rs.getObject("animal_id", UUID.class),
        rs.getObject("recorded_on", LocalDate.class), rs.getBigDecimal("liters"),
        session == null ? null : MilkSession.valueOf(session), rs.getString("notes"),
        rs.getObject("actor_user_id", UUID.class),
        rs.getObject("recorded_at", OffsetDateTime.class).toInstant());
  }
}
