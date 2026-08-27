package com.gerenciadorrural.modules.organizations.domain;
import java.util.Optional; import java.util.UUID;
public interface TenantContextResolverRepository { Optional<ResolvedTenantContext> resolve(UUID userId, UUID organizationId, UUID farmId); }
