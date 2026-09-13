package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.gerenciadorrural.modules.herd.application.HerdPlannerService;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerItem;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerStatus;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd/planner-items")
public class HerdPlannerController {
  private static final Set<String> LIST_PARAMETERS =
      Set.of("status", "type", "animalId", "from", "to", "page", "size");

  private final HerdPlannerService service;

  public HerdPlannerController(HerdPlannerService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<Item> create(
      @ResolvedTenantContext TenantContext context, @RequestBody PlannerRequest request) {
    HerdPlannerService.Command command = request.toCommand();
    HerdPlannerService.Mutation result = service.create(context, command);
    return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(Item.of(result.item(), command.operationId(), result.replay()));
  }

  @PatchMapping("/{id}")
  ResponseEntity<Item> correct(
      @ResolvedTenantContext TenantContext context,
      @PathVariable UUID id,
      @RequestBody PlannerRequest request) {
    HerdPlannerService.Command command = request.toCommand();
    return mutation(service.correct(context, id, command), command.operationId());
  }

  @PostMapping("/{id}/completion")
  ResponseEntity<Item> complete(
      @ResolvedTenantContext TenantContext context,
      @PathVariable UUID id,
      @RequestBody @JsonDeserialize(using = HerdPlannerTransitionDeserializer.class)
          Transition command) {
    return mutation(
        service.transition(
            context,
            id,
            command.operationId(),
            command.expectedVersion(),
            HerdPlannerStatus.COMPLETED),
        command.operationId());
  }

  @PostMapping("/{id}/cancellation")
  ResponseEntity<Item> cancel(
      @ResolvedTenantContext TenantContext context,
      @PathVariable UUID id,
      @RequestBody @JsonDeserialize(using = HerdPlannerTransitionDeserializer.class)
          Transition command) {
    return mutation(
        service.transition(
            context,
            id,
            command.operationId(),
            command.expectedVersion(),
            HerdPlannerStatus.CANCELLED),
        command.operationId());
  }

  @GetMapping("/{id}")
  ResponseEntity<Item> detail(@ResolvedTenantContext TenantContext context, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Item.of(service.detail(context, id), null, false));
  }

  @GetMapping
  ResponseEntity<Page> list(
      @ResolvedTenantContext TenantContext context,
      @RequestParam(required = false) HerdPlannerStatus status,
      @RequestParam(required = false) HerdPlannerType type,
      @RequestParam(required = false) UUID animalId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    if (!LIST_PARAMETERS.containsAll(request.getParameterMap().keySet())
        || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) {
      throw new HerdAnimalQueryException();
    }
    var result = service.page(context, status, type, animalId, from, to, page, size);
    int totalPages = Math.toIntExact((result.totalElements() + result.size() - 1) / result.size());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new Page(
                result.items().stream().map(item -> Item.of(item, null, false)).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                totalPages));
  }

  private ResponseEntity<Item> mutation(HerdPlannerService.Mutation result, UUID operationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Item.of(result.item(), operationId, result.replay()));
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  @JsonDeserialize(using = HerdPlannerTransitionDeserializer.class)
  public record Transition(UUID operationId, long expectedVersion) {}

  @JsonDeserialize(using = HerdPlannerRequestDeserializer.class)
  public record PlannerRequest(
      UUID operationId,
      Long expectedVersion,
      HerdPlannerType type,
      String title,
      String notes,
      LocalDate scheduledFor,
      UUID animalId) {
    HerdPlannerService.Command toCommand() {
      return new HerdPlannerService.Command(
          operationId, expectedVersion, type, title, notes, scheduledFor, animalId);
    }
  }

  public record Page(List<Item> items, int page, int size, long totalElements, int totalPages) {}

  public record Item(
      UUID id,
      UUID operationId,
      HerdPlannerType type,
      String title,
      String notes,
      LocalDate scheduledFor,
      HerdPlannerStatus status,
      UUID animalId,
      long version,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt,
      Instant cancelledAt,
      boolean replay) {
    static Item of(HerdPlannerItem item, UUID operationId, boolean replay) {
      return new Item(
          item.id(),
          operationId,
          item.type(),
          item.title(),
          item.notes(),
          item.scheduledFor(),
          item.status(),
          item.animalId(),
          item.version(),
          item.createdAt(),
          item.updatedAt(),
          item.completedAt(),
          item.cancelledAt(),
          replay);
    }
  }
}
