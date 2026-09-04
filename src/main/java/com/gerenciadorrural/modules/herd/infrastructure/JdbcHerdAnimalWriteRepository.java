package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteRepository;
import com.gerenciadorrural.modules.herd.domain.NewHerdAnimal;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcHerdAnimalWriteRepository implements HerdAnimalWriteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcHerdAnimalWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HerdAnimalSummary insert(NewHerdAnimal animal) {
        return jdbc.queryForObject("""
                insert into app.animals (
                    id, tenant_id, farm_id, identification, name, sex, birth_date
                ) values (
                    :id, :tenantId, :farmId, :identification, :name, :sex, :birthDate
                )
                returning id, identification, name, sex, birth_date, status, version
                """, parameters(animal), this::mapAnimal);
    }

    @Override
    public Optional<HerdAnimalSummary> findById(TenantId tenantId, UUID farmId, UUID id) {
        return jdbc.query("""
                select id, identification, name, sex, birth_date, status, version
                from app.animals
                where tenant_id = :tenantId and farm_id = :farmId and id = :id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId.value())
                .addValue("farmId", farmId)
                .addValue("id", id), this::mapAnimal).stream().findFirst();
    }

    private static MapSqlParameterSource parameters(NewHerdAnimal animal) {
        return new MapSqlParameterSource()
                .addValue("id", animal.id())
                .addValue("tenantId", animal.tenantId().value())
                .addValue("farmId", animal.farmId())
                .addValue("identification", animal.identification())
                .addValue("name", animal.name())
                .addValue("sex", animal.sex().name())
                .addValue("birthDate", animal.birthDate());
    }

    private HerdAnimalSummary mapAnimal(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new HerdAnimalSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("identification"),
                resultSet.getString("name"),
                HerdAnimalSex.valueOf(resultSet.getString("sex")),
                resultSet.getObject("birth_date", java.time.LocalDate.class),
                HerdAnimalStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version")
        );
    }
}
