package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdGroupRepository implements HerdGroupRepository {
  private static final String COLUMNS = "id,name,kind,status,sex,animal_status,min_age_months,max_age_months,only_reproduction_active,only_missing_profile,version";
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcHerdGroupRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<HerdGroup> find(TenantId tenant, UUID farm, UUID id, boolean lock) {
    return jdbc.query("select " + COLUMNS + " from app.herd_groups where tenant_id=:tenantId and farm_id=:farmId and id=:id"
        + (lock ? " for update" : ""), parameters(tenant, farm, id), JdbcHerdGroupRepository::map)
        .stream().findFirst();
  }

  @Override
  public HerdGroup insert(TenantId tenant, UUID farm, HerdGroup group) {
    return jdbc.query("insert into app.herd_groups(id,tenant_id,farm_id,name,kind,status,sex,animal_status,min_age_months,max_age_months,only_reproduction_active,only_missing_profile)"
        + " values(:id,:tenantId,:farmId,:name,:kind,:status,:sex,:animalStatus,:minAge,:maxAge,:reproduction,:missing) returning " + COLUMNS,
        values(tenant, farm, group), JdbcHerdGroupRepository::map).getFirst();
  }

  @Override
  public Optional<HerdGroup> update(TenantId tenant, UUID farm, HerdGroup group, long expectedVersion) {
    return jdbc.query("update app.herd_groups set name=:name,status=:status,sex=:sex,animal_status=:animalStatus,min_age_months=:minAge,max_age_months=:maxAge,only_reproduction_active=:reproduction,only_missing_profile=:missing,version=version+1,updated_at=current_timestamp"
        + " where tenant_id=:tenantId and farm_id=:farmId and id=:id and version=:expected and status='ACTIVE' returning " + COLUMNS,
        values(tenant, farm, group).addValue("expected", expectedVersion), JdbcHerdGroupRepository::map)
        .stream().findFirst();
  }

  @Override
  public List<HerdGroup> list(TenantId tenant, UUID farm, int limit, long offset) {
    return jdbc.query("select " + COLUMNS + " from app.herd_groups where tenant_id=:tenantId and farm_id=:farmId and status='ACTIVE' order by lower(name),id limit :limit offset :offset",
        parameters(tenant, farm, null).addValue("limit", limit).addValue("offset", offset), JdbcHerdGroupRepository::map);
  }

  @Override
  public long count(TenantId tenant, UUID farm) {
    Long value = jdbc.queryForObject("select count(*) from app.herd_groups where tenant_id=:tenantId and farm_id=:farmId and status='ACTIVE'",
        parameters(tenant, farm, null), Long.class);
    return value == null ? 0 : value;
  }

  @Override
  public boolean animalExists(TenantId tenant, UUID farm, UUID animal) {
    Boolean value = jdbc.queryForObject("select exists(select 1 from app.animals where tenant_id=:tenantId and farm_id=:farmId and id=:id)",
        parameters(tenant, farm, animal), Boolean.class);
    return Boolean.TRUE.equals(value);
  }

  @Override
  public boolean memberExists(TenantId tenant, UUID farm, UUID group, UUID animal) {
    Boolean value = jdbc.queryForObject("select exists(select 1 from app.herd_group_members where tenant_id=:tenantId and farm_id=:farmId and group_id=:id and animal_id=:animalId)",
        parameters(tenant, farm, group).addValue("animalId", animal), Boolean.class);
    return Boolean.TRUE.equals(value);
  }

  @Override
  public void addMember(TenantId tenant, UUID farm, UUID group, UUID animal) {
    jdbc.update("insert into app.herd_group_members(tenant_id,farm_id,group_id,animal_id) values(:tenantId,:farmId,:id,:animalId)",
        parameters(tenant, farm, group).addValue("animalId", animal));
  }

  @Override
  public void removeMember(TenantId tenant, UUID farm, UUID group, UUID animal) {
    jdbc.update("delete from app.herd_group_members where tenant_id=:tenantId and farm_id=:farmId and group_id=:id and animal_id=:animalId",
        parameters(tenant, farm, group).addValue("animalId", animal));
  }

  @Override
  public Optional<HerdGroup> bumpVersion(TenantId tenant, UUID farm, UUID group, long expectedVersion) {
    return jdbc.query("update app.herd_groups set version=version+1,updated_at=current_timestamp where tenant_id=:tenantId and farm_id=:farmId and id=:id and status='ACTIVE' and version=:expected returning " + COLUMNS,
        parameters(tenant, farm, group).addValue("expected", expectedVersion), JdbcHerdGroupRepository::map)
        .stream().findFirst();
  }

  @Override
  public HerdAnimalPage animals(TenantId tenant, UUID farm, HerdGroup group, LocalDate reference,
      int page, int size) {
    var parameters = parameters(tenant, farm, group.id()).addValue("reference", reference)
        .addValue("limit", size).addValue("offset", (long) page * size);
    String where = group.kind() == HerdGroup.Kind.MANUAL
        ? " and exists(select 1 from app.herd_group_members m where m.tenant_id=a.tenant_id and m.farm_id=a.farm_id and m.group_id=:id and m.animal_id=a.id)"
        : smartWhere(group.rules(), parameters);
    String from = " from app.animals a left join app.paddocks p on p.tenant_id=a.tenant_id and p.farm_id=a.farm_id and p.id=a.paddock_id where a.tenant_id=:tenantId and a.farm_id=:farmId" + where;
    List<HerdAnimalSummary> items = jdbc.query("select a.id,a.identification,a.name,a.sex,a.birth_date,a.status,a.version,p.id paddock_id,p.name paddock_name,p.code paddock_code,p.status paddock_status,p.version paddock_version"
        + from + " order by lower(a.identification),a.id limit :limit offset :offset",
        parameters, JdbcHerdGroupRepository::animal);
    Long total = jdbc.queryForObject("select count(*)" + from, parameters, Long.class);
    return new HerdAnimalPage(items, page, size, total == null ? 0 : total);
  }

  private static String smartWhere(HerdGroup.Rules rules, MapSqlParameterSource parameters) {
    StringBuilder where = new StringBuilder();
    if (rules.sex() != null) {
      where.append(" and a.sex=:sex");
      parameters.addValue("sex", rules.sex().name());
    }
    if (rules.status() != null) {
      where.append(" and a.status=:animalStatus");
      parameters.addValue("animalStatus", rules.status().name());
    }
    String age = "(extract(year from age(:reference,a.birth_date))::int*12+extract(month from age(:reference,a.birth_date))::int)";
    if (rules.minAgeMonths() != null) {
      where.append(" and a.birth_date<=:reference and ").append(age).append(">=:minAge");
      parameters.addValue("minAge", rules.minAgeMonths());
    }
    if (rules.maxAgeMonths() != null) {
      where.append(" and a.birth_date<=:reference and ").append(age).append("<=:maxAge");
      parameters.addValue("maxAge", rules.maxAgeMonths());
    }
    if (rules.onlyReproductionActive())
      where.append(" and exists(select 1 from app.animal_pregnancies pregnancy where pregnancy.tenant_id=a.tenant_id and pregnancy.farm_id=a.farm_id and pregnancy.mother_animal_id=a.id and pregnancy.status in ('POSSIBLE','CONFIRMED'))");
    if (rules.onlyMissingProfile())
      where.append(" and (a.birth_date is null or not exists(select 1 from app.animal_maternal_relations relation where relation.tenant_id=a.tenant_id and relation.calf_animal_id=a.id))");
    return where.toString();
  }

  private static HerdGroup map(ResultSet row, int number) throws SQLException {
    String sex = row.getString("sex");
    String animalStatus = row.getString("animal_status");
    var rules = new HerdGroup.Rules(sex == null ? null : HerdAnimalSex.valueOf(sex),
        animalStatus == null ? null : HerdAnimalStatus.valueOf(animalStatus),
        row.getObject("min_age_months", Integer.class), row.getObject("max_age_months", Integer.class),
        row.getBoolean("only_reproduction_active"), row.getBoolean("only_missing_profile"));
    return new HerdGroup(row.getObject("id", UUID.class), row.getString("name"),
        HerdGroup.Kind.valueOf(row.getString("kind")), HerdGroup.Status.valueOf(row.getString("status")),
        rules, row.getLong("version"));
  }

  private static HerdAnimalSummary animal(ResultSet row, int number) throws SQLException {
    UUID paddockId = row.getObject("paddock_id", UUID.class);
    PaddockSummary paddock = paddockId == null ? null : new PaddockSummary(paddockId,
        row.getString("paddock_name"), row.getString("paddock_code"),
        PaddockStatus.valueOf(row.getString("paddock_status")), row.getLong("paddock_version"));
    return new HerdAnimalSummary(row.getObject("id", UUID.class), row.getString("identification"),
        row.getString("name"), HerdAnimalSex.valueOf(row.getString("sex")),
        row.getObject("birth_date", LocalDate.class), HerdAnimalStatus.valueOf(row.getString("status")),
        row.getLong("version"), paddock);
  }

  private static MapSqlParameterSource parameters(TenantId tenant, UUID farm, UUID id) {
    return new MapSqlParameterSource().addValue("tenantId", tenant.value())
        .addValue("farmId", farm).addValue("id", id);
  }

  private static MapSqlParameterSource values(TenantId tenant, UUID farm, HerdGroup group) {
    return parameters(tenant, farm, group.id()).addValue("name", group.name())
        .addValue("kind", group.kind().name()).addValue("status", group.status().name())
        .addValue("sex", group.rules().sex() == null ? null : group.rules().sex().name())
        .addValue("animalStatus", group.rules().status() == null ? null : group.rules().status().name())
        .addValue("minAge", group.rules().minAgeMonths()).addValue("maxAge", group.rules().maxAgeMonths())
        .addValue("reproduction", group.rules().onlyReproductionActive())
        .addValue("missing", group.rules().onlyMissingProfile());
  }
}
