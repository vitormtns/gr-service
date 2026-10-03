package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ReadHerdAgeIntelligence {
  private static final Set<String> READ = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER");
  private final TenantTransactionExecutor transactions;
  private final HerdAgeIntelligenceRepository repository;
  private final Clock clock;
  public ReadHerdAgeIntelligence(TenantTransactionExecutor transactions,
      HerdAgeIntelligenceRepository repository, Clock clock) {
    this.transactions = transactions; this.repository = repository; this.clock = clock;
  }
  public Page transitions(TenantContext context, int horizonDays, int page, int size) {
    if (!READ.contains(context.role())) throw new HerdMovementForbiddenException();
    if (horizonDays < 0 || horizonDays > 366 || page < 0 || size < 1 || size > 100
        || (long) page * size > Integer.MAX_VALUE) throw new HerdDashboardQueryInvalidException();
    LocalDate reference = LocalDate.now(clock);
    return transactions.execute(context, () -> new Page(reference, horizonDays,
        repository.transitions(context.tenantId(), context.farmId(), reference, horizonDays,
            size, (long) page * size).stream().map(a -> new Item(a.animalId(), a.identification(),
                a.name(), AgeIntelligence.derive(a.birthDate(), reference))).toList(),
        page, size, repository.transitionCount(context.tenantId(), context.farmId(), reference, horizonDays)));
  }
  public record Item(UUID animalId, String identification, String name, AgeIntelligence age) {}
  public record Page(LocalDate referenceDate, int horizonDays, List<Item> items,
      int page, int size, long totalElements) {
    public int totalPages() { return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size); }
  }
}
