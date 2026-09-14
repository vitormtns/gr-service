package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAgendaRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAgendaSource;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerStatus;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import com.gerenciadorrural.modules.herd.domain.PendingWorkType;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReadHerdAgenda {

  private static final Set<String> READ_ROLES =
      Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER");

  private final TenantTransactionExecutor transactions;
  private final HerdAgendaRepository repository;
  private final Clock clock;
  private final int weighingDueDays;
  private final int calvingUpcomingDays;

  public ReadHerdAgenda(
      TenantTransactionExecutor transactions,
      HerdAgendaRepository repository,
      Clock clock,
      @Value("${herd.pending-work.weighing-due-days:90}") int weighingDueDays,
      @Value("${herd.pending-work.calving-upcoming-days:14}") int calvingUpcomingDays) {
    this.transactions = transactions;
    this.repository = repository;
    this.clock = clock;
    this.weighingDueDays = weighingDueDays;
    this.calvingUpcomingDays = calvingUpcomingDays;
  }

  public Result page(
      TenantContext context,
      HerdAgendaSource source,
      HerdPlannerType type,
      UUID animalId,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    if (!READ_ROLES.contains(context.role())) {
      throw new HerdPlannerExceptions.Forbidden();
    }
    if (page < 0
        || size < 1
        || size > 100
        || (long) page * size > Integer.MAX_VALUE
        || from != null && to != null && from.isAfter(to)) {
      throw new HerdPlannerExceptions.QueryInvalid();
    }

    return transactions.execute(
        context,
        () -> {
          LocalDate referenceDate = LocalDate.now(clock);
          var rows =
              repository.page(
                  context.tenantId(),
                  context.farmId(),
                  referenceDate,
                  weighingDueDays,
                  calvingUpcomingDays,
                  source,
                  type,
                  animalId,
                  from,
                  to,
                  size,
                  (long) page * size);
          long total =
              repository.count(
                  context.tenantId(),
                  context.farmId(),
                  referenceDate,
                  weighingDueDays,
                  calvingUpcomingDays,
                  source,
                  type,
                  animalId,
                  from,
                  to);
          return new Result(
              rows.stream().map(Item::from).toList(), page, size, total, pages(total, size));
        });
  }

  private static int pages(long total, int size) {
    return Math.toIntExact((total + size - 1) / size);
  }

  public record Result(List<Item> items, int page, int size, long totalElements, int totalPages) {}

  public record Item(
      HerdAgendaSource source,
      String kind,
      LocalDate operationalDate,
      String stableId,
      UUID animalId,
      String summary,
      String identification,
      String name,
      UUID plannerItemId,
      PendingWorkType pendingWorkType,
      UUID pregnancyId,
      HerdPlannerStatus status) {
    static Item from(HerdAgendaRepository.Row row) {
      return new Item(
          row.source(),
          row.kind(),
          row.operationalDate(),
          row.stableId(),
          row.animalId(),
          row.summary(),
          row.identification(),
          row.name(),
          row.plannerItemId(),
          row.pendingWorkType(),
          row.pregnancyId(),
          row.status());
    }
  }
}
