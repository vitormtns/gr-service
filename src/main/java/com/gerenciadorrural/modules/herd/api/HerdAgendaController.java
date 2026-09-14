package com.gerenciadorrural.modules.herd.api;

import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd/agenda")
public class HerdAgendaController {
  private final ReadHerdAgenda agenda;

  public HerdAgendaController(ReadHerdAgenda agenda) {
    this.agenda = agenda;
  }

  @GetMapping
  ResponseEntity<?> page(
      @ResolvedTenantContext TenantContext c,
      @RequestParam(required = false) HerdAgendaSource source,
      @RequestParam(required = false) HerdPlannerType type,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest r) {
    if (!Set.of("source", "type", "animalId", "from", "to", "page", "size")
            .containsAll(r.getParameterMap().keySet())
        || r.getParameterMap().values().stream().anyMatch(x -> x.length != 1))
      throw new HerdAnimalQueryException();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(agenda.page(c, source, type, animalId, from, to, page, size));
  }
}
