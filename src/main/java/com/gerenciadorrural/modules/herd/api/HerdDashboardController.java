package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.modules.herd.application.ReadHerdDashboard;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardPeriod;
import com.gerenciadorrural.modules.herd.domain.HerdReportCategory;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/herd/dashboard")
public class HerdDashboardController {
  private final ReadHerdDashboard dashboard;

  public HerdDashboardController(ReadHerdDashboard dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/overview")
  ResponseEntity<?> overview(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) HerdDashboardPeriod period,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) HerdReportCategory category,
      @RequestParam(required = false) HerdAnimalSex sex,
      @RequestParam(required = false) UUID paddockId,
      HttpServletRequest request) {
    parameters(request, "period", "from", "to", "category", "sex", "paddockId");
    return ok(dashboard.overview(context, period, from, to, category, sex, paddockId));
  }

  @GetMapping("/activity")
  ResponseEntity<?> activity(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) HerdDashboardPeriod period,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      HttpServletRequest request) {
    parameters(request, "period", "from", "to");
    return ok(dashboard.activity(context, period, from, to));
  }

  @GetMapping("/attention")
  ResponseEntity<?> attention(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(defaultValue = "5") int previewSize,
      HttpServletRequest request) {
    parameters(request, "previewSize");
    return ok(dashboard.attention(context, previewSize));
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
