package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.MaternalRelationRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMaternalRelationRepository implements MaternalRelationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMaternalRelationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private MapSqlParameterSource params(TenantId tenantId) {
        return new MapSqlParameterSource("tenant", tenantId.value());
    }

    @Override
    public void lockFarm(TenantId tenantId, UUID farmId) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(:key, 0))",
                new MapSqlParameterSource("key", "maternal:" + tenantId.value() + ":" + farmId),
                rs -> {});
    }

    @Override
    public void insert(TenantId tenantId, UUID motherId, UUID calfId, UUID pregnancyId) {
        jdbc.update("""
                insert into app.animal_maternal_relations
                    (tenant_id, mother_animal_id, calf_animal_id, pregnancy_id)
                values (:tenant, :mother, :calf, :pregnancy)
                """, params(tenantId).addValue("mother", motherId).addValue("calf", calfId)
                .addValue("pregnancy", pregnancyId));
    }

    @Override
    public Optional<UUID> motherId(TenantId tenantId, UUID calfId) {
        return relation(tenantId, calfId).map(Relation::motherId);
    }

    @Override
    public Optional<Relation> relation(TenantId tenantId, UUID calfId) {
        return jdbc.query("""
                select mother_animal_id, pregnancy_id from app.animal_maternal_relations
                 where tenant_id = :tenant and calf_animal_id = :calf
                """, params(tenantId).addValue("calf", calfId), (rs, row) ->
                new Relation(rs.getObject("mother_animal_id", UUID.class),
                        rs.getObject("pregnancy_id", UUID.class))).stream().findFirst();
    }

    @Override
    public List<UUID> calfIds(TenantId tenantId, UUID motherId, int size, long offset) {
        return jdbc.query("""
                select calf_animal_id from app.animal_maternal_relations
                 where tenant_id = :tenant and mother_animal_id = :mother
                 order by created_at desc, calf_animal_id desc limit :size offset :offset
                """, params(tenantId).addValue("mother", motherId).addValue("size", size)
                .addValue("offset", offset), (rs, row) -> rs.getObject(1, UUID.class));
    }

    @Override
    public boolean change(TenantId tenantId, UUID calfId, UUID beforeMotherId, UUID afterMotherId) {
        return jdbc.update("""
                update app.animal_maternal_relations set mother_animal_id = :after
                 where tenant_id = :tenant and calf_animal_id = :calf
                   and mother_animal_id = :before and pregnancy_id is null
                """, params(tenantId).addValue("calf", calfId).addValue("before", beforeMotherId)
                .addValue("after", afterMotherId)) == 1;
    }

    @Override
    public boolean remove(TenantId tenantId, UUID calfId, UUID beforeMotherId) {
        return jdbc.update("""
                delete from app.animal_maternal_relations
                 where tenant_id = :tenant and calf_animal_id = :calf
                   and mother_animal_id = :before and pregnancy_id is null
                """, params(tenantId).addValue("calf", calfId).addValue("before", beforeMotherId)) == 1;
    }
}
