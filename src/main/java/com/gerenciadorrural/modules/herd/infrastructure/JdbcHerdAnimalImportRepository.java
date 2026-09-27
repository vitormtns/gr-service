package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.Array;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdAnimalImportRepository implements HerdAnimalImportRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcHerdAnimalImportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private MapSqlParameterSource params(TenantId tenantId, UUID farmId) {
        return new MapSqlParameterSource("tenant", tenantId.value()).addValue("farm", farmId);
    }

    @Override
    public void lock(TenantId tenantId, UUID farmId, UUID operationId) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(:key, 0))",
                new MapSqlParameterSource("key", tenantId.value() + ":" + farmId + ":" + operationId),
                rs -> {});
    }

    @Override
    public void lockMaternalGraph(TenantId tenantId, UUID farmId) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(:key, 0))",
                new MapSqlParameterSource("key", "maternal:" + tenantId.value() + ":" + farmId),
                rs -> {});
    }

    @Override
    public Optional<Receipt> find(TenantId tenantId, UUID farmId, UUID operationId) {
        return jdbc.query("""
                select payload_hash, animal_ids from app.herd_animal_imports
                where tenant_id = :tenant and farm_id = :farm and operation_id = :operation
                """, params(tenantId, farmId).addValue("operation", operationId), (rs, row) -> {
                    Array array = rs.getArray("animal_ids");
                    try {
                        return new Receipt(rs.getString("payload_hash"), List.copyOf(Arrays.asList((UUID[]) array.getArray())));
                    } finally {
                        array.free();
                    }
                }).stream().findFirst();
    }

    @Override
    public void save(TenantId tenantId, UUID farmId, UUID operationId, Receipt receipt) {
        jdbc.getJdbcTemplate().execute((java.sql.Connection connection) -> {
            try (var statement = connection.prepareStatement("""
                    insert into app.herd_animal_imports(tenant_id, farm_id, operation_id, payload_hash, animal_ids)
                    values (?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, farmId);
                statement.setObject(3, operationId);
                statement.setString(4, receipt.payloadHash());
                Array ids = connection.createArrayOf("uuid", receipt.animalIds().toArray());
                try {
                    statement.setArray(5, ids);
                    statement.executeUpdate();
                } finally {
                    ids.free();
                }
            }
            return null;
        });
    }

    @Override
    public Optional<HerdAnimalSummary> findByIdentification(TenantId tenantId, UUID farmId, String identification) {
        return jdbc.query("""
                select id, identification, name, sex, birth_date, status, version
                from app.animals
                where tenant_id = :tenant and farm_id = :farm
                  and lower(regexp_replace(identification, '(^[[:space:]]+|[[:space:]]+$)', '', 'g'))
                      = lower(:identification)
                """, params(tenantId, farmId).addValue("identification", identification), (rs, row) ->
                new HerdAnimalSummary(rs.getObject("id", UUID.class), rs.getString("identification"),
                        rs.getString("name"), HerdAnimalSex.valueOf(rs.getString("sex")),
                        rs.getObject("birth_date", LocalDate.class), HerdAnimalStatus.valueOf(rs.getString("status")),
                        rs.getLong("version"))).stream().findFirst();
    }

    @Override
    public Optional<UUID> motherId(TenantId tenantId, UUID animalId) {
        return jdbc.query("""
                select mother_animal_id from app.animal_maternal_relations
                where tenant_id = :tenant and calf_animal_id = :animal
                """, new MapSqlParameterSource("tenant", tenantId.value()).addValue("animal", animalId),
                (rs, row) -> rs.getObject(1, UUID.class)).stream().findFirst();
    }

    @Override
    public void linkMother(TenantId tenantId, UUID motherId, UUID calfId) {
        jdbc.update("""
                insert into app.animal_maternal_relations(tenant_id, mother_animal_id, calf_animal_id)
                values (:tenant, :mother, :calf)
                """, new MapSqlParameterSource("tenant", tenantId.value())
                .addValue("mother", motherId).addValue("calf", calfId));
    }
}
