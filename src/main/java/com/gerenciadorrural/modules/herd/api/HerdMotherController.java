package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.herd.application.CorrectCurrentFarmAnimalMother;
import com.gerenciadorrural.modules.herd.application.HerdAnimalCommandInvalidException;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/herd/animals")
public class HerdMotherController {
    private final CorrectCurrentFarmAnimalMother service;

    public HerdMotherController(CorrectCurrentFarmAnimalMother service) {
        this.service = service;
    }

    @PutMapping("/{id}/mother")
    public ResponseEntity<CorrectCurrentFarmAnimalMother.Result> correct(
            @ResolvedTenantContext TenantContext context, @PathVariable UUID id,
            @RequestBody Request request, HttpServletRequest servletRequest) {
        if (!servletRequest.getParameterMap().isEmpty()) throw new HerdAnimalCommandInvalidException();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.execute(
                context, id, request.operationId(), request.expectedVersion(), request.motherId()));
    }

    @JsonDeserialize(using = HerdMotherRequestDeserializer.class)
    public record Request(UUID operationId, Long expectedVersion, UUID motherId) {}
}
