package com.gerenciadorrural.modules.organizations.infrastructure;
import com.gerenciadorrural.modules.organizations.domain.*; import com.gerenciadorrural.shared.infrastructure.database.TransactionalCurrentUserContext; import org.springframework.jdbc.core.namedparam.*; import org.springframework.stereotype.Repository; import java.util.*;
@Repository public class JdbcTenantContextResolverRepository implements TenantContextResolverRepository {
 private final NamedParameterJdbcTemplate jdbc; private final TransactionalCurrentUserContext context;
 public JdbcTenantContextResolverRepository(NamedParameterJdbcTemplate jdbc, TransactionalCurrentUserContext context){this.jdbc=jdbc;this.context=context;}
 public Optional<ResolvedTenantContext> resolve(UUID userId, UUID organizationId, UUID farmId){context.configure(userId); return jdbc.query("select * from app.resolve_current_user_tenant_context(:organizationId,:farmId)",new MapSqlParameterSource().addValue("organizationId",organizationId).addValue("farmId",farmId),(r,n)->new ResolvedTenantContext(r.getObject("organization_id",UUID.class),r.getString("organization_name"),r.getObject("farm_id",UUID.class),r.getString("farm_name"),r.getObject("membership_id",UUID.class),r.getString("role_key"),r.getString("farm_scope_mode"))).stream().findFirst();}
}
