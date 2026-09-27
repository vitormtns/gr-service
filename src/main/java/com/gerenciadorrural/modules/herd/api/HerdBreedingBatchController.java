package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.herd.application.BatchBreedCurrentFarmAnimals;
import com.gerenciadorrural.modules.herd.domain.ReproductionServiceType;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/herd/breedings/batch")
public class HerdBreedingBatchController {
    private final BatchBreedCurrentFarmAnimals service;

    public HerdBreedingBatchController(BatchBreedCurrentFarmAnimals service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BatchBreedCurrentFarmAnimals.Result> breed(
            @ResolvedTenantContext TenantContext context, @RequestBody Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.execute(context,
                request.operationId(), request.serviceType(), request.serviceOn(), request.sireReference(),
                request.expectedCalvingOn(), request.notes(), request.mothers()));
    }

    @JsonDeserialize(using = HerdBreedingBatchRequestDeserializer.class)
    public record Request(UUID operationId, ReproductionServiceType serviceType, LocalDate serviceOn,
            String sireReference, LocalDate expectedCalvingOn, String notes,
            List<BatchBreedCurrentFarmAnimals.Mother> mothers) {}
}
