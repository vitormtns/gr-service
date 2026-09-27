package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.HerdBreedingBatchRepository;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHerdBreedingBatchRepository implements HerdBreedingBatchRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcHerdBreedingBatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private MapSqlParameterSource params(TenantId tenantId, UUID farmId, UUID operationId) {
        return new MapSqlParameterSource("tenant", tenantId.value())
                .addValue("farm", farmId).addValue("operation", operationId);
    }

    @Override
    public void lock(TenantId tenantId, UUID farmId, UUID operationId) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(:key, 0))",
                new MapSqlParameterSource("key", tenantId.value() + ":" + farmId + ":" + operationId),
                rs -> {});
    }

    @Override
    public Optional<Receipt> find(TenantId tenantId, UUID farmId, UUID operationId) {
        return jdbc.query("""
                select payload_hash, mother_ids, pregnancy_ids from app.herd_breeding_batches
                 where tenant_id = :tenant and farm_id = :farm and operation_id = :operation
                """, params(tenantId, farmId, operationId), (rs, row) -> {
                    Array mothers = rs.getArray("mother_ids");
                    Array pregnancies = rs.getArray("pregnancy_ids");
                    try {
                        return new Receipt(rs.getString("payload_hash"),
                                List.copyOf(Arrays.asList((UUID[]) mothers.getArray())),
                                List.copyOf(Arrays.asList((UUID[]) pregnancies.getArray())));
                    } finally {
                        mothers.free();
                        pregnancies.free();
                    }
                }).stream().findFirst();
    }

    @Override
    public void save(TenantId tenantId, UUID farmId, UUID operationId, Receipt receipt) {
        jdbc.getJdbcTemplate().execute((java.sql.Connection connection) -> {
            try (var statement = connection.prepareStatement("""
                    insert into app.herd_breeding_batches
                        (tenant_id, farm_id, operation_id, payload_hash, mother_ids, pregnancy_ids)
                    values (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, farmId);
                statement.setObject(3, operationId);
                statement.setString(4, receipt.payloadHash());
                Array mothers = connection.createArrayOf("uuid", receipt.motherIds().toArray());
                Array pregnancies = connection.createArrayOf("uuid", receipt.pregnancyIds().toArray());
                try {
                    statement.setArray(5, mothers);
                    statement.setArray(6, pregnancies);
                    statement.executeUpdate();
                } finally {
                    mothers.free();
                    pregnancies.free();
                }
            }
            return null;
        });
    }
}
