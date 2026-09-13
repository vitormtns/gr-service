package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.modules.herd.application.ReadHerdReports;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.domain.HerdReportRepository.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd/reports")
public class HerdReportController {
  private final ReadHerdReports reports;

  public HerdReportController(ReadHerdReports reports) {
    this.reports = reports;
  }

  @GetMapping("/herd-position")
  ResponseEntity<?> herdPosition(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) HerdReportCategory category,
      @RequestParam(required = false) HerdAnimalSex sex,
      @RequestParam(required = false) UUID paddockId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "category", "sex", "paddockId", "page", "size");
    return ok(reports.herdPosition(context, category, sex, paddockId, page, size));
  }

  @GetMapping("/lifecycle")
  ResponseEntity<?> lifecycle(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) LifecycleEvent event,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "from", "to", "event", "animalId", "page", "size");
    return ok(reports.lifecycle(context, from, to, event, animalId, page, size));
  }

  @GetMapping("/movements")
  ResponseEntity<?> movements(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(required = false) UUID sourcePaddockId,
      @RequestParam(required = false) UUID destinationPaddockId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(
        request,
        "from",
        "to",
        "animalId",
        "sourcePaddockId",
        "destinationPaddockId",
        "page",
        "size");
    return ok(
        reports.movements(
            context,
            from,
            to,
            animalId,
            sourcePaddockId,
            destinationPaddockId,
            page,
            size));
  }

  @GetMapping("/transfers")
  ResponseEntity<?> transfers(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) TransferDirection direction,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "from", "to", "direction", "animalId", "page", "size");
    return ok(reports.transfers(context, from, to, direction, animalId, page, size));
  }

  @GetMapping("/weights")
  ResponseEntity<?> weights(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(required = false) HerdReportCategory category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "from", "to", "animalId", "category", "page", "size");
    return ok(reports.weights(context, from, to, animalId, category, page, size));
  }

  @GetMapping("/health")
  ResponseEntity<?> health(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) HealthTreatmentType treatmentType,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "from", "to", "treatmentType", "animalId", "page", "size");
    return ok(reports.health(context, from, to, treatmentType, animalId, page, size));
  }

  @GetMapping("/reproduction")
  ResponseEntity<?> reproduction(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID motherId,
      @RequestParam(required = false) ReproductionServiceType serviceType,
      @RequestParam(required = false) PregnancyStatus pregnancyStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(
        request,
        "from",
        "to",
        "motherId",
        "serviceType",
        "pregnancyStatus",
        "page",
        "size");
    return ok(
        reports.reproduction(
            context, from, to, motherId, serviceType, pregnancyStatus, page, size));
  }

  @GetMapping("/planner")
  ResponseEntity<?> planner(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) HerdPlannerStatus status,
      @RequestParam(required = false) HerdPlannerType type,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "from", "to", "status", "type", "animalId", "page", "size");
    return ok(reports.planner(context, from, to, status, type, animalId, page, size));
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
}
