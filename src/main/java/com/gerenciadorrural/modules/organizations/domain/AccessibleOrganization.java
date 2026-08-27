package com.gerenciadorrural.modules.organizations.domain;

import java.util.Objects;
import java.util.UUID;

public record AccessibleOrganization(
        UUID organizationId,
        String organizationName,
        UUID membershipId,
        String role,
        String farmScopeMode
) {

    public AccessibleOrganization {
        Objects.requireNonNull(organizationId, "organizationId é obrigatório");
        Objects.requireNonNull(organizationName, "organizationName é obrigatório");
        Objects.requireNonNull(membershipId, "membershipId é obrigatório");
        Objects.requireNonNull(role, "role é obrigatório");
        Objects.requireNonNull(farmScopeMode, "farmScopeMode é obrigatório");
    }
}
