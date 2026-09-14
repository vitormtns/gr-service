package com.gerenciadorrural.modules.herd.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class HerdPlannerService {
  private static final Set<String> READ = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER"),
      WRITE = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");
  private final TenantTransactionExecutor tx;
  private final HerdPlannerRepository repo;
  private final HerdPlannerOperationRepository operations;
  private final HerdAnimalProfileRepository animals;
  private final ObjectMapper json;

  public HerdPlannerService(
      TenantTransactionExecutor tx,
      HerdPlannerRepository repo,
      HerdPlannerOperationRepository operations,
      HerdAnimalProfileRepository animals,
      ObjectMapper json) {
    this.tx = tx;
    this.repo = repo;
    this.operations = operations;
    this.animals = animals;
    this.json = json;
  }

  public Mutation create(TenantContext c, Command x) {
    write(c);
    if (x != null && x.expectedVersion() != null) throw new HerdAnimalCommandInvalidException();
    Valid v = valid(x, false);
    return tx.execute(
        c,
        () -> {
          operations.lock(c.tenantId(), c.farmId(), v.operation);
          var replay = replay(c, v.operation, HerdPlannerOperation.Type.CREATE, v.payload, null);
          if (replay != null) return new Mutation(replay, true);
          animal(c, v.animal);
          var item =
              repo.insert(
                  new HerdPlannerItem(
                      UUID.randomUUID(),
                      c.tenantId(),
                      c.farmId(),
                      v.animal,
                      v.type,
                      v.title,
                      v.notes,
                      v.date,
                      HerdPlannerStatus.OPEN,
                      0,
                      c.userId(),
                      null,
                      null,
                      null,
                      null));
          save(c, v.operation, HerdPlannerOperation.Type.CREATE, item, v.payload);
          return new Mutation(item, false);
        });
  }

  public Mutation correct(TenantContext c, UUID id, Command x) {
    write(c);
    Valid v = valid(x, true);
    return tx.execute(
        c,
        () -> {
          operations.lock(c.tenantId(), c.farmId(), v.operation);
          var replay = replay(c, v.operation, HerdPlannerOperation.Type.CORRECT, v.payload, id);
          if (replay != null) return new Mutation(replay, true);
          var current =
              repo.find(c.tenantId(), c.farmId(), id)
                  .orElseThrow(HerdPlannerExceptions.NotFound::new);
          if (current.status() != HerdPlannerStatus.OPEN)
            throw new HerdPlannerExceptions.InvalidState();
          animal(c, v.animal);
          var item =
              repo.update(
                      c.tenantId(),
                      c.farmId(),
                      id,
                      v.version,
                      v.type,
                      v.title,
                      v.notes,
                      v.date,
                      v.animal)
                  .orElseThrow(HerdPlannerExceptions.Conflict::new);
          save(c, v.operation, HerdPlannerOperation.Type.CORRECT, item, v.payload);
          return new Mutation(item, false);
        });
  }

  public Mutation transition(
      TenantContext c, UUID id, UUID operation, long version, HerdPlannerStatus status) {
    write(c);
    if (id == null
        || operation == null
        || version < 0
        || status == null
        || status == HerdPlannerStatus.OPEN) throw new HerdAnimalCommandInvalidException();
    return tx.execute(
        c,
        () -> {
          String payload = canonical(Map.of("action", status.name(), "expectedVersion", version));
          HerdPlannerOperation.Type type =
              status == HerdPlannerStatus.COMPLETED
                  ? HerdPlannerOperation.Type.COMPLETE
                  : HerdPlannerOperation.Type.CANCEL;
          operations.lock(c.tenantId(), c.farmId(), operation);
          var replay = replay(c, operation, type, payload, id);
          if (replay != null) return new Mutation(replay, true);
          var current =
              repo.find(c.tenantId(), c.farmId(), id)
                  .orElseThrow(HerdPlannerExceptions.NotFound::new);
          if (current.status() != HerdPlannerStatus.OPEN)
            throw new HerdPlannerExceptions.InvalidState();
          var item =
              repo.transition(c.tenantId(), c.farmId(), id, version, status)
                  .orElseThrow(HerdPlannerExceptions.Conflict::new);
          save(c, operation, type, item, payload);
          return new Mutation(item, false);
        });
  }

  public Page page(
      TenantContext c,
      HerdPlannerStatus s,
      HerdPlannerType t,
      UUID a,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    read(c);
    if (!paging(page, size) || from != null && to != null && from.isAfter(to))
      throw new HerdPlannerExceptions.QueryInvalid();
    return tx.execute(
        c,
        () ->
            new Page(
                repo.list(c.tenantId(), c.farmId(), s, t, a, from, to, size, (long) page * size),
                page,
                size,
                repo.count(c.tenantId(), c.farmId(), s, t, a, from, to)));
  }

  public HerdPlannerItem detail(TenantContext c, UUID id) {
    read(c);
    return tx.execute(
        c,
        () ->
            repo.find(c.tenantId(), c.farmId(), id)
                .orElseThrow(HerdPlannerExceptions.NotFound::new));
  }

  private HerdPlannerItem replay(
      TenantContext c,
      UUID operation,
      HerdPlannerOperation.Type type,
      String payload,
      UUID itemId) {
    var old = operations.find(c.tenantId(), c.farmId(), operation);
    if (old.isEmpty()) return null;
    var value = old.get();
    if (value.type() != type
        || !value.payload().equals(payload)
        || (itemId != null && !value.plannerItemId().equals(itemId)))
      throw new HerdPlannerExceptions.Conflict();
    return repo.find(c.tenantId(), c.farmId(), value.plannerItemId())
        .filter(x -> x.version() >= value.resultingVersion())
        .orElseThrow(HerdPlannerExceptions.Conflict::new);
  }

  private void save(
      TenantContext c,
      UUID operation,
      HerdPlannerOperation.Type type,
      HerdPlannerItem item,
      String payload) {
    operations.save(
        new HerdPlannerOperation(
            c.tenantId(), c.farmId(), operation, type, item.id(), payload, item.version(), null));
  }

  private Valid valid(Command x, boolean version) {
    if (x == null
        || x.operationId() == null
        || (version && (x.expectedVersion() == null || x.expectedVersion() < 0))
        || x.type() == null
        || x.scheduledFor() == null) throw new HerdAnimalCommandInvalidException();
    String title = text(x.title(), 160, true), notes = text(x.notes(), 2000, false);
    Map<String, Object> payload = new TreeMap<>();
    payload.put("type", x.type().name());
    payload.put("title", title);
    payload.put("notes", notes);
    payload.put("scheduledFor", x.scheduledFor().toString());
    payload.put("animalId", x.animalId() == null ? null : x.animalId().toString());
    payload.put("expectedVersion", version ? x.expectedVersion() : null);
    return new Valid(
        x.operationId(),
        version ? x.expectedVersion() : 0,
        x.type(),
        title,
        notes,
        x.scheduledFor(),
        x.animalId(),
        canonical(payload));
  }

  private void animal(TenantContext c, UUID id) {
    if (id != null && animals.findById(c.tenantId(), c.farmId(), id).isEmpty())
      throw new HerdAnimalNotFoundException();
  }

  private String canonical(Object x) {
    try {
      return json.writeValueAsString(x);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String text(String x, int n, boolean required) {
    if (x == null) {
      if (required) throw new HerdAnimalCommandInvalidException();
      return null;
    }
    String y = x.strip();
    if ((required && y.isEmpty())
        || (!required && y.isEmpty())
        || y.indexOf('\0') >= 0
        || y.codePointCount(0, y.length()) > n) throw new HerdAnimalCommandInvalidException();
    return y;
  }

  private static boolean paging(int p, int s) {
    return p >= 0 && s > 0 && s <= 100 && (long) p * s <= Integer.MAX_VALUE;
  }

  private static void read(TenantContext c) {
    if (!READ.contains(c.role())) throw new HerdPlannerExceptions.Forbidden();
  }

  private static void write(TenantContext c) {
    if (!WRITE.contains(c.role())) throw new HerdPlannerExceptions.Forbidden();
  }

  private record Valid(
      UUID operation,
      long version,
      HerdPlannerType type,
      String title,
      String notes,
      LocalDate date,
      UUID animal,
      String payload) {}

  public record Command(
      UUID operationId,
      Long expectedVersion,
      HerdPlannerType type,
      String title,
      String notes,
      LocalDate scheduledFor,
      UUID animalId) {}

  public record Mutation(HerdPlannerItem item, boolean replay) {}

  public record Page(List<HerdPlannerItem> items, int page, int size, long totalElements) {}
}
