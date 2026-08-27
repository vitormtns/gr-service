package com.gerenciadorrural.modules.organizations.api;

import com.gerenciadorrural.modules.organizations.application.ListCurrentUserOrganizations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/organizations")
public class CurrentUserOrganizationsController {

    private final ListCurrentUserOrganizations listCurrentUserOrganizations;

    public CurrentUserOrganizationsController(ListCurrentUserOrganizations listCurrentUserOrganizations) {
        this.listCurrentUserOrganizations = listCurrentUserOrganizations;
    }

    @GetMapping
    public CurrentUserOrganizationsResponse list() {
        return CurrentUserOrganizationsResponse.from(listCurrentUserOrganizations.execute());
    }
}
