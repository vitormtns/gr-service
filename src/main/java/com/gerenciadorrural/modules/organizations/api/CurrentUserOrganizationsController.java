package com.gerenciadorrural.modules.organizations.api;

import com.gerenciadorrural.modules.organizations.application.ListCurrentUserOrganizations;
import com.gerenciadorrural.modules.organizations.application.ListAccessibleFarmsForOrganization;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/organizations")
public class CurrentUserOrganizationsController {

    private final ListCurrentUserOrganizations listCurrentUserOrganizations;
    private final ListAccessibleFarmsForOrganization listAccessibleFarmsForOrganization;

    public CurrentUserOrganizationsController(
            ListCurrentUserOrganizations listCurrentUserOrganizations,
            ListAccessibleFarmsForOrganization listAccessibleFarmsForOrganization
    ) {
        this.listCurrentUserOrganizations = listCurrentUserOrganizations;
        this.listAccessibleFarmsForOrganization = listAccessibleFarmsForOrganization;
    }

    @GetMapping
    public CurrentUserOrganizationsResponse list() {
        return CurrentUserOrganizationsResponse.from(listCurrentUserOrganizations.execute());
    }

    @GetMapping("/{organizationId}/farms")
    public AccessibleFarmsResponse listFarms(@PathVariable java.util.UUID organizationId) {
        return AccessibleFarmsResponse.from(listAccessibleFarmsForOrganization.execute(organizationId));
    }
}
