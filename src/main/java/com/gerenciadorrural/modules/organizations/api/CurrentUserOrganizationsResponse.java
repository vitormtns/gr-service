package com.gerenciadorrural.modules.organizations.api;

import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganization;

import java.util.List;

public record CurrentUserOrganizationsResponse(List<Item> items) {

    static CurrentUserOrganizationsResponse from(List<AccessibleOrganization> organizations) {
        return new CurrentUserOrganizationsResponse(organizations.stream()
                .map(organization -> new Item(
                        organization.organizationId().toString(),
                        organization.organizationName(),
                        organization.membershipId().toString(),
                        organization.role(),
                        organization.farmScopeMode()
                ))
                .toList());
    }

    public record Item(
            String organizationId,
            String organizationName,
            String membershipId,
            String role,
            String farmScopeMode
    ) {
    }
}
