package com.gerenciadorrural.modules.farms.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.farms.application.GetCurrentFarmProfile;
import com.gerenciadorrural.modules.farms.application.UpdateCurrentFarmProfile;
import com.gerenciadorrural.modules.farms.domain.FarmProfile;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/farms/current")
public class FarmProfileController {
    private final GetCurrentFarmProfile getCurrentFarmProfile;
    private final UpdateCurrentFarmProfile updateCurrentFarmProfile;

    public FarmProfileController(GetCurrentFarmProfile getCurrentFarmProfile, UpdateCurrentFarmProfile updateCurrentFarmProfile) {
        this.getCurrentFarmProfile = getCurrentFarmProfile;
        this.updateCurrentFarmProfile = updateCurrentFarmProfile;
    }

    @GetMapping
    public ResponseEntity<Response> current(@ResolvedTenantContext TenantContext context) {
        return ok(getCurrentFarmProfile.execute(context));
    }

    @PatchMapping
    public ResponseEntity<Response> update(
            @ResolvedTenantContext TenantContext context,
            @Valid @RequestBody UpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        String queryString = servletRequest.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            throw new FarmProfileUpdateRequestException();
        }
        return ok(updateCurrentFarmProfile.execute(context, request.name(), request.expectedVersion()));
    }

    private ResponseEntity<Response> ok(FarmProfile profile) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Response.from(profile));
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonDeserialize(using = FarmProfileUpdateRequestDeserializer.class)
    public record UpdateRequest(@NotBlank String name, @NotNull @PositiveOrZero Long expectedVersion) {

        @JsonIgnore
        @AssertTrue
        public boolean isNormalizedNameWithinLimit() {
            return name == null || name.trim().length() <= 255;
        }
    }

    public record Response(String id, String organizationId, String name, String status, long version) {
        static Response from(FarmProfile profile) {
            return new Response(profile.id().toString(), profile.organizationId().toString(), profile.name(), profile.status(), profile.version());
        }
    }
}
