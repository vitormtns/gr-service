package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcHerdAnimalProfileRepository implements HerdAnimalProfileRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcHerdAnimalProfileRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<HerdAnimalSummary> findById(TenantId tenantId, UUID farmId, UUID id) {
        return jdbc.query("""
                select id, identification, name, sex, birth_date, status, version from app.animals
                where tenant_id=:tenantId and farm_id=:farmId and id=:id
                """, parameters(tenantId, farmId, id), JdbcHerdAnimalProfileRepository::map).stream().findFirst();
    }
    @Override public Optional<HerdAnimalSummary> findByIdForCorrection(TenantId tenantId, UUID farmId, UUID id) {
        return jdbc.query("""
                select id, identification, name, sex, birth_date, status, version from app.animals
                where tenant_id=:tenantId and farm_id=:farmId and id=:id for update
                """, parameters(tenantId, farmId, id), JdbcHerdAnimalProfileRepository::map).stream().findFirst();
    }
    @Override public Optional<HerdAnimalSummary> update(TenantId tenantId, UUID farmId, UUID id, long expectedVersion, String identification, String name, HerdAnimalSex sex, LocalDate birthDate) {
        var p=parameters(tenantId,farmId,id).addValue("expectedVersion",expectedVersion).addValue("identification",identification).addValue("name",name).addValue("sex",sex.name()).addValue("birthDate",birthDate);
        try { return jdbc.query("""
                update app.animals set identification=:identification, name=:name, sex=:sex, birth_date=:birthDate,
                    version=version+1, updated_at=current_timestamp
                where tenant_id=:tenantId and farm_id=:farmId and id=:id and version=:expectedVersion
                returning id, identification, name, sex, birth_date, status, version
                """,p,JdbcHerdAnimalProfileRepository::map).stream().findFirst();
        } catch (DataIntegrityViolationException exception) {
            if (identificationConflict(exception)) throw new HerdAnimalWriteConflictException(HerdAnimalWriteConflictException.Type.IDENTIFICATION_CONFLICT, exception);
            throw exception;
        }
    }
    @Override public Optional<HerdAnimalSummary> updateStatus(TenantId tenantId,UUID farmId,UUID id,long expectedVersion,HerdAnimalStatus status){return jdbc.query("update app.animals set status=:status, version=version+1, updated_at=current_timestamp where tenant_id=:tenantId and farm_id=:farmId and id=:id and version=:expectedVersion returning id, identification, name, sex, birth_date, status, version",parameters(tenantId,farmId,id).addValue("expectedVersion",expectedVersion).addValue("status",status.name()),JdbcHerdAnimalProfileRepository::map).stream().findFirst();}
    private static MapSqlParameterSource parameters(TenantId tenantId, UUID farmId, UUID id) { return new MapSqlParameterSource().addValue("tenantId",tenantId.value()).addValue("farmId",farmId).addValue("id",id); }
    private static HerdAnimalSummary map(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new HerdAnimalSummary(r.getObject("id",UUID.class),r.getString("identification"),r.getString("name"),HerdAnimalSex.valueOf(r.getString("sex")),r.getObject("birth_date",LocalDate.class),HerdAnimalStatus.valueOf(r.getString("status")),r.getLong("version"));}
    private static boolean identificationConflict(Throwable e) { for(Throwable c=e;c!=null;c=c.getCause()) if(c instanceof PSQLException p && "23505".equals(p.getSQLState()) && p.getServerErrorMessage()!=null && "animals_tenant_farm_identification_unique".equals(p.getServerErrorMessage().getConstraint())) return true; return false; }
}
