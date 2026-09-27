package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.herd.application.HerdAnimalCommandInvalidException;
import com.gerenciadorrural.modules.herd.application.RecordCurrentFarmAnimalNote;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/herd/animals")
public class HerdAnimalNoteController {
    private final RecordCurrentFarmAnimalNote service;

    public HerdAnimalNoteController(RecordCurrentFarmAnimalNote service) {
        this.service = service;
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<RecordCurrentFarmAnimalNote.Result> record(
            @ResolvedTenantContext TenantContext context, @PathVariable UUID id,
            @RequestBody Request body, HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw new HerdAnimalCommandInvalidException();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.execute(
                context, id, body.operationId(), body.expectedVersion(), body.occurredOn(), body.notes()));
    }

    @JsonDeserialize(using = HerdAnimalNoteRequestDeserializer.class)
    public record Request(UUID operationId, Long expectedVersion, LocalDate occurredOn, String notes) {}
}
