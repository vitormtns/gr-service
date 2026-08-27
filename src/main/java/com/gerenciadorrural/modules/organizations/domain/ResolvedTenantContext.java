package com.gerenciadorrural.modules.organizations.domain;
import java.util.UUID;
public record ResolvedTenantContext(UUID organizationId, String organizationName, UUID farmId, String farmName, UUID membershipId, String role, String farmScopeMode) { }
