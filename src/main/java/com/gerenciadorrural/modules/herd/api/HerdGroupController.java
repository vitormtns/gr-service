package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.HerdAnimalCommandInvalidException;
import com.gerenciadorrural.modules.herd.application.HerdGroupService;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdGroup;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/herd/groups")
public class HerdGroupController {
  private static final ObjectMapper STRICT = new ObjectMapper(JsonFactory.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
  private final HerdGroupService groups;

  public HerdGroupController(HerdGroupService groups) {
    this.groups = groups;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<?> create(@ResolvedTenantContext TenantContext context, @RequestBody String body) {
    JsonNode node = object(body, Set.of("id", "name", "kind", "rules"), Set.of("id", "name", "kind"));
    HerdGroupService.Result result = groups.create(context, uuid(node, "id"), string(node, "name"),
        enumeration(node, "kind", HerdGroup.Kind.class), rules(node.get("rules")));
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .cacheControl(CacheControl.noStore()).body(result.group());
  }

  @GetMapping
  ResponseEntity<?> list(@ResolvedTenantContext TenantContext context,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "page", "size");
    return ok(groups.list(context, page, size));
  }

  @GetMapping("/{id}")
  ResponseEntity<?> get(@ResolvedTenantContext TenantContext context, @PathVariable UUID id) {
    return ok(groups.get(context, id));
  }

  @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<?> update(@ResolvedTenantContext TenantContext context, @PathVariable UUID id,
      @RequestBody String body) {
    JsonNode node = object(body, Set.of("expectedVersion", "name", "rules"),
        Set.of("expectedVersion", "name"));
    return ok(groups.update(context, id, version(node), string(node, "name"), rules(node.get("rules"))));
  }

  @PostMapping(value = "/{id}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<?> archive(@ResolvedTenantContext TenantContext context, @PathVariable UUID id,
      @RequestBody String body) {
    return ok(groups.archive(context, id, version(object(body,
        Set.of("expectedVersion"), Set.of("expectedVersion")))));
  }

  @PutMapping(value = "/{id}/animals/{animalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<?> addMember(@ResolvedTenantContext TenantContext context, @PathVariable UUID id,
      @PathVariable UUID animalId, @RequestBody String body) {
    return ok(groups.membership(context, id, animalId, version(object(body,
        Set.of("expectedVersion"), Set.of("expectedVersion"))), true));
  }

  @DeleteMapping(value = "/{id}/animals/{animalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<?> removeMember(@ResolvedTenantContext TenantContext context, @PathVariable UUID id,
      @PathVariable UUID animalId, @RequestBody String body) {
    return ok(groups.membership(context, id, animalId, version(object(body,
        Set.of("expectedVersion"), Set.of("expectedVersion"))), false));
  }

  @GetMapping("/{id}/animals")
  ResponseEntity<?> animals(@ResolvedTenantContext TenantContext context, @PathVariable UUID id,
      @RequestParam(required = false) LocalDate referenceDate,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    parameters(request, "referenceDate", "page", "size");
    var result = groups.animals(context, id, referenceDate, page, size);
    return ok(new Animals(result.items().stream().map(HerdAnimalController.Item::from).toList(),
        result.page(), result.size(), result.totalElements(), result.totalPages(),
        "CURRENT_STATE_AGED_AT_REFERENCE"));
  }

  private static ResponseEntity<?> ok(Object body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  private static JsonNode object(String body, Set<String> allowed, Set<String> required) {
    try (JsonParser parser = STRICT.getFactory().createParser(body)) {
      JsonNode node = STRICT.readTree(parser);
      if (node == null || !node.isObject())
        throw new IllegalArgumentException();
      Iterator<String> fields = node.fieldNames();
      while (fields.hasNext()) if (!allowed.contains(fields.next())) throw new IllegalArgumentException();
      for (String field : required) if (!node.hasNonNull(field)) throw new IllegalArgumentException();
      return node;
    } catch (Exception error) {
      throw new HerdAnimalCommandInvalidException();
    }
  }

  private static HerdGroup.Rules rules(JsonNode node) {
    if (node == null || node.isNull()) return HerdGroup.Rules.empty();
    if (!node.isObject()) throw new HerdAnimalCommandInvalidException();
    Set<String> allowed = Set.of("sex", "status", "minAgeMonths", "maxAgeMonths",
        "onlyReproductionActive", "onlyMissingProfile");
    Iterator<String> fields = node.fieldNames();
    while (fields.hasNext()) if (!allowed.contains(fields.next())) throw new HerdAnimalCommandInvalidException();
    return new HerdGroup.Rules(optionalEnum(node, "sex", HerdAnimalSex.class),
        optionalEnum(node, "status", HerdAnimalStatus.class), optionalInt(node, "minAgeMonths"),
        optionalInt(node, "maxAgeMonths"), optionalBoolean(node, "onlyReproductionActive"),
        optionalBoolean(node, "onlyMissingProfile"));
  }

  private static UUID uuid(JsonNode node, String field) {
    try { return UUID.fromString(string(node, field)); }
    catch (IllegalArgumentException error) { throw new HerdAnimalCommandInvalidException(); }
  }

  private static String string(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual()) throw new HerdAnimalCommandInvalidException();
    return value.textValue();
  }

  private static Long version(JsonNode node) {
    JsonNode value = node.get("expectedVersion");
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong())
      throw new HerdAnimalCommandInvalidException();
    return value.longValue();
  }

  private static Integer optionalInt(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new HerdAnimalCommandInvalidException();
    return value.intValue();
  }

  private static boolean optionalBoolean(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null) return false;
    if (!value.isBoolean()) throw new HerdAnimalCommandInvalidException();
    return value.booleanValue();
  }

  private static <E extends Enum<E>> E optionalEnum(JsonNode node, String field, Class<E> type) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : enumeration(node, field, type);
  }

  private static <E extends Enum<E>> E enumeration(JsonNode node, String field, Class<E> type) {
    try { return Enum.valueOf(type, string(node, field)); }
    catch (IllegalArgumentException error) { throw new HerdAnimalCommandInvalidException(); }
  }

  private static void parameters(HttpServletRequest request, String... allowed) {
    if (!Set.of(allowed).containsAll(request.getParameterMap().keySet())
        || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1))
      throw new HerdAnimalQueryException();
  }

  public record Animals(java.util.List<HerdAnimalController.Item> items, int page, int size,
      long totalElements, int totalPages, String positionSemantics) {}
}
