package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.herd.application.MilkService;
import com.gerenciadorrural.modules.herd.domain.MilkSession;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd")
public class MilkController {
  private final MilkService milk;

  public MilkController(MilkService milk) {
    this.milk = milk;
  }

  @PostMapping("/animals/{id}/milk-records")
  ResponseEntity<?> record(@ResolvedTenantContext TenantContext context,
      @PathVariable UUID id, @RequestBody MilkCommand request) {
    return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
        .body(milk.record(context, id, new MilkService.Command(request.operationId(),
            request.expectedVersion(), request.recordedOn(), request.liters(),
            request.session(), request.notes())));
  }

  @GetMapping("/animals/{id}/milk-records")
  ResponseEntity<?> history(@ResolvedTenantContext TenantContext context,
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
    parameters(request, "page", "size");
    return ok(milk.history(context, id, page, size));
  }

  @GetMapping("/milk/overview")
  ResponseEntity<?> overview(@ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate referenceDate, HttpServletRequest request) {
    parameters(request, "referenceDate");
    return ok(milk.overview(context, referenceDate));
  }

  @GetMapping("/animals/{id}/milk-summary")
  ResponseEntity<?> animalSummary(@ResolvedTenantContext TenantContext context,
      @PathVariable UUID id, @RequestParam(required = false) LocalDate referenceDate,
      HttpServletRequest request) {
    parameters(request, "referenceDate");
    return ok(milk.animalSummary(context, id, referenceDate));
  }

  private static ResponseEntity<?> ok(Object body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  private static void parameters(HttpServletRequest request, String... allowed) {
    if (!Set.of(allowed).containsAll(request.getParameterMap().keySet())
        || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) {
      throw new HerdAnimalQueryException();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  @JsonDeserialize(using = MilkCommandDeserializer.class)
  public record MilkCommand(UUID operationId, Long expectedVersion, LocalDate recordedOn,
      BigDecimal liters, MilkSession session, String notes) {}
}
