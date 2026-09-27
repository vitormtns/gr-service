package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.herd.application.ImportCurrentFarmAnimals;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/herd/animals/imports")
public class HerdAnimalImportController {
    private final ImportCurrentFarmAnimals importer;

    public HerdAnimalImportController(ImportCurrentFarmAnimals importer) {
        this.importer = importer;
    }

    @PostMapping
    public ResponseEntity<ImportCurrentFarmAnimals.Result> create(
            @ResolvedTenantContext TenantContext context, @RequestBody ImportRequest request) {
        var result = importer.execute(context, request.operationId(), request.animals());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @JsonDeserialize(using = HerdAnimalImportRequestDeserializer.class)
    public record ImportRequest(UUID operationId, List<ImportCurrentFarmAnimals.Row> animals) {}
}
