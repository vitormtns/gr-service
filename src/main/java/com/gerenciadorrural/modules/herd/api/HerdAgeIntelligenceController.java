package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd/age-intelligence")
public class HerdAgeIntelligenceController {
  private final ReadHerdAgeIntelligence age;
  public HerdAgeIntelligenceController(ReadHerdAgeIntelligence age) { this.age = age; }
  @GetMapping("/transitions")
  ResponseEntity<?> transitions(@ResolvedTenantContext TenantContext context,
      @RequestParam(defaultValue = "15") int horizonDays,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    if (!Set.of("horizonDays", "page", "size").containsAll(request.getParameterMap().keySet())
        || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
      throw new HerdDashboardQueryInvalidException();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(age.transitions(context, horizonDays, page, size));
  }
}
