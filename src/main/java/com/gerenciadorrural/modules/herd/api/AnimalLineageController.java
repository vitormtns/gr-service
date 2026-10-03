package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd/animals")
public class AnimalLineageController {
  private final ReadAnimalLineage lineage;
  public AnimalLineageController(ReadAnimalLineage lineage) { this.lineage = lineage; }
  @GetMapping("/{id}/lineage")
  ResponseEntity<?> read(@ResolvedTenantContext TenantContext context, @PathVariable UUID id,
      @RequestParam(defaultValue = "5") int depth, @RequestParam(defaultValue = "100") int limit,
      HttpServletRequest request) {
    if (!Set.of("depth", "limit").containsAll(request.getParameterMap().keySet())
        || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
      throw new HerdAnimalQueryException();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lineage.read(context,id,depth,limit));
  }
}
