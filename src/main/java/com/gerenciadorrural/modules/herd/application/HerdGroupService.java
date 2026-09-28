package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalPage;
import com.gerenciadorrural.modules.herd.domain.HerdGroup;
import com.gerenciadorrural.modules.herd.domain.HerdGroupRepository;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class HerdGroupService {
  private static final Set<String> WRITE = Set.of("OWNER", "ADMIN", "MANAGER");
  private static final Set<String> READ = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER");
  private final TenantTransactionExecutor transactions;
  private final HerdGroupRepository groups;
  private final Clock clock;

  public HerdGroupService(TenantTransactionExecutor transactions, HerdGroupRepository groups,
      Clock clock) {
    this.transactions = transactions;
    this.groups = groups;
    this.clock = clock;
  }

  public Result create(TenantContext context, UUID id, String name, HerdGroup.Kind kind,
      HerdGroup.Rules rules) {
    write(context);
    if (id == null || kind == null) throw new HerdAnimalCommandInvalidException();
    HerdGroup requested = new HerdGroup(id, name(name), kind, HerdGroup.Status.ACTIVE,
        rules(kind, rules), 0);
    return transactions.execute(context, () -> {
      var existing = groups.find(context.tenantId(), context.farmId(), id, false);
      if (existing.isPresent()) {
        if (existing.get().equals(requested)) return new Result(existing.get(), false);
        throw new HerdGroupConflictException();
      }
      try {
        return new Result(groups.insert(context.tenantId(), context.farmId(), requested), true);
      } catch (DataIntegrityViolationException error) {
        throw new HerdGroupConflictException();
      }
    });
  }

  public HerdGroup update(TenantContext context, UUID id, Long expectedVersion, String name,
      HerdGroup.Rules rules) {
    write(context);
    version(id, expectedVersion);
    if (name == null) throw new HerdAnimalCommandInvalidException();
    return transactions.execute(context, () -> {
      HerdGroup current = active(context, id, true);
      if (current.version() != expectedVersion) throw new HerdAnimalVersionConflictException();
      HerdGroup target = new HerdGroup(id, name(name), current.kind(), current.status(),
          rules(current.kind(), rules), current.version());
      if (current.equals(target)) return current;
      return save(context, target, expectedVersion);
    });
  }

  public HerdGroup archive(TenantContext context, UUID id, Long expectedVersion) {
    write(context);
    version(id, expectedVersion);
    return transactions.execute(context, () -> {
      HerdGroup current = active(context, id, true);
      if (current.version() != expectedVersion) throw new HerdAnimalVersionConflictException();
      return save(context, new HerdGroup(id, current.name(), current.kind(),
          HerdGroup.Status.ARCHIVED, current.rules(), current.version()), expectedVersion);
    });
  }

  public HerdGroup membership(TenantContext context, UUID groupId, UUID animalId,
      Long expectedVersion, boolean add) {
    write(context);
    version(groupId, expectedVersion);
    if (animalId == null) throw new HerdAnimalCommandInvalidException();
    return transactions.execute(context, () -> {
      HerdGroup group = active(context, groupId, true);
      if (group.kind() != HerdGroup.Kind.MANUAL) throw new HerdGroupConflictException();
      if (group.version() != expectedVersion) throw new HerdAnimalVersionConflictException();
      if (!groups.animalExists(context.tenantId(), context.farmId(), animalId))
        throw new HerdGroupNotFoundException();
      boolean exists = groups.memberExists(context.tenantId(), context.farmId(), groupId, animalId);
      if (exists == add) return group;
      if (add) groups.addMember(context.tenantId(), context.farmId(), groupId, animalId);
      else groups.removeMember(context.tenantId(), context.farmId(), groupId, animalId);
      return groups.bumpVersion(context.tenantId(), context.farmId(), groupId, expectedVersion)
          .orElseThrow(HerdAnimalVersionConflictException::new);
    });
  }

  public HerdGroup get(TenantContext context, UUID id) {
    read(context);
    if (id == null) throw new HerdAnimalCommandInvalidException();
    return transactions.execute(context, () -> active(context, id, false));
  }

  public Page list(TenantContext context, int page, int size) {
    read(context);
    page(page, size);
    return transactions.execute(context, () -> new Page(
        groups.list(context.tenantId(), context.farmId(), size, (long) page * size), page, size,
        groups.count(context.tenantId(), context.farmId())));
  }

  public HerdAnimalPage animals(TenantContext context, UUID id, LocalDate referenceDate,
      int page, int size) {
    read(context);
    page(page, size);
    if (id == null || (referenceDate != null && referenceDate.isAfter(LocalDate.now(clock))))
      throw new HerdAnimalCommandInvalidException();
    LocalDate reference = referenceDate == null ? LocalDate.now(clock) : referenceDate;
    return transactions.execute(context, () -> groups.animals(context.tenantId(), context.farmId(),
        active(context, id, false), reference, page, size));
  }

  private HerdGroup active(TenantContext context, UUID id, boolean lock) {
    return groups.find(context.tenantId(), context.farmId(), id, lock)
        .filter(group -> group.status() == HerdGroup.Status.ACTIVE)
        .orElseThrow(HerdGroupNotFoundException::new);
  }

  private HerdGroup save(TenantContext context, HerdGroup group, long expectedVersion) {
    try {
      return groups.update(context.tenantId(), context.farmId(), group, expectedVersion)
          .orElseThrow(HerdAnimalVersionConflictException::new);
    } catch (DataIntegrityViolationException error) {
      throw new HerdGroupConflictException();
    }
  }

  private static HerdGroup.Rules rules(HerdGroup.Kind kind, HerdGroup.Rules value) {
    HerdGroup.Rules rules = value == null ? HerdGroup.Rules.empty() : value;
    if ((rules.minAgeMonths() != null && rules.minAgeMonths() < 0)
        || (rules.maxAgeMonths() != null && rules.maxAgeMonths() < 0)
        || (rules.minAgeMonths() != null && rules.maxAgeMonths() != null
            && rules.minAgeMonths() > rules.maxAgeMonths())
        || (kind == HerdGroup.Kind.MANUAL && !Objects.equals(rules, HerdGroup.Rules.empty())))
      throw new HerdAnimalCommandInvalidException();
    return rules;
  }

  private static String name(String value) {
    if (value == null) throw new HerdAnimalCommandInvalidException();
    String normalized = PosixEdgeWhitespace.trim(value);
    if (normalized.isEmpty() || normalized.indexOf('\0') >= 0
        || normalized.codePointCount(0, normalized.length()) > 120)
      throw new HerdAnimalCommandInvalidException();
    return normalized;
  }

  private static void version(UUID id, Long expected) {
    if (id == null || expected == null || expected < 0)
      throw new HerdAnimalCommandInvalidException();
  }

  private static void page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw new HerdAnimalCommandInvalidException();
  }

  private static void read(TenantContext context) {
    if (!READ.contains(context.role())) throw new HerdGroupForbiddenException();
  }

  private static void write(TenantContext context) {
    if (!WRITE.contains(context.role())) throw new HerdGroupForbiddenException();
  }

  public record Result(HerdGroup group, boolean created) {}
  public record Page(List<HerdGroup> items, int page, int size, long totalElements) {}
}
