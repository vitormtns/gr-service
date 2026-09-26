package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAgendaRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAgendaSource;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository;
import com.gerenciadorrural.modules.herd.domain.BrucellosisPrimaryCompliance;
import com.gerenciadorrural.modules.herd.domain.BrucellosisPrimaryState;
import com.gerenciadorrural.modules.herd.domain.AgePolicy;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerStatus;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import com.gerenciadorrural.modules.herd.domain.PendingWorkType;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
  private final HerdManagementRepository herd;
  private final Clock clock;
  private final int weighingDueDays;
  private final int calvingUpcomingDays;

  public ReadHerdAgenda(
      TenantTransactionExecutor transactions,
      HerdAgendaRepository repository,
      HerdManagementRepository herd,
      Clock clock,
      @Value("${herd.pending-work.weighing-due-days:90}") int weighingDueDays,
      @Value("${herd.pending-work.calving-upcoming-days:14}") int calvingUpcomingDays) {
    this.transactions = transactions;
    this.repository = repository;
    this.herd = herd;
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
          if (isBrucellosisEligible(source, type)) {
            return merged(context, source, type, animalId, from, to, page, size, referenceDate);
          }
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

  private static boolean isBrucellosisEligible(HerdAgendaSource source, HerdPlannerType type) {
    return (source == null || source == HerdAgendaSource.DERIVED)
        && (type == null || type == HerdPlannerType.VACCINATION);
  }

  private Result merged(
      TenantContext context,
      HerdAgendaSource source,
      HerdPlannerType type,
      UUID animalId,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      LocalDate referenceDate) {
    long legacyTotal =
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
    List<HerdAgendaRepository.Row> brucellosis = brucellosisRows(context, animalId, from, to, referenceDate);
    long globalTotal = legacyTotal + brucellosis.size();
    long offset = (long) page * size;
    if (offset >= globalTotal) {
      return new Result(List.of(), page, size, globalTotal, pages(globalTotal, size));
    }
    long requestedPrefix = Math.min(offset + size, legacyTotal);
    if (requestedPrefix > Integer.MAX_VALUE) {
      throw new HerdPlannerExceptions.QueryInvalid();
    }
    int prefix = (int) requestedPrefix;
    List<HerdAgendaRepository.Row> all = new ArrayList<>(repository.page(
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
        prefix,
        0L));
    all.addAll(brucellosis);
    all.sort(GLOBAL_ORDER);
    List<Item> items = all.subList((int) offset, (int) Math.min(offset + size, all.size())).stream()
        .map(Item::from)
        .toList();
    return new Result(items, page, size, globalTotal, pages(globalTotal, size));
  }

  private List<HerdAgendaRepository.Row> brucellosisRows(
      TenantContext context, UUID animalId, LocalDate from, LocalDate to, LocalDate referenceDate) {
    List<HerdAgendaRepository.Row> rows = new ArrayList<>();
    for (HerdManagementRepository.BrucellosisPendingCandidate candidate
        : herd.brucellosisPendingCandidates(context.tenantId(), context.farmId(), referenceDate, animalId)) {
      BrucellosisPrimaryState state = BrucellosisPrimaryCompliance.evaluate(
          candidate.sex(), candidate.birthDate(), referenceDate, candidate.treatments());
      PendingWorkType pendingType = state == BrucellosisPrimaryState.DUE_IN_WINDOW
          ? PendingWorkType.BRUCELLOSIS_DUE
          : state == BrucellosisPrimaryState.WINDOW_MISSED
              ? PendingWorkType.BRUCELLOSIS_WINDOW_MISSED
              : null;
      if (pendingType == null) {
        continue;
      }
      LocalDate operationalDate = AgePolicy.reachesMonthsOn(candidate.birthDate(), 9);
      if ((from != null && operationalDate.isBefore(from))
          || (to != null && operationalDate.isAfter(to))) {
        continue;
      }
      rows.add(new HerdAgendaRepository.Row(
          HerdAgendaSource.DERIVED,
          HerdPlannerType.VACCINATION.name(),
          operationalDate,
          candidate.animalId() + ":" + pendingType.name(),
          candidate.animalId(),
          pendingType == PendingWorkType.BRUCELLOSIS_DUE
              ? "Brucelose na janela de vacinação"
              : "Janela de brucelose perdida",
          candidate.identification(),
          candidate.name(),
          null,
          pendingType,
          null,
          null));
    }
    return rows;
  }

  private static final Comparator<HerdAgendaRepository.Row> GLOBAL_ORDER = Comparator
      .comparing(HerdAgendaRepository.Row::operationalDate)
      .thenComparing(row -> row.source().name())
      .thenComparing(HerdAgendaRepository.Row::kind)
      .thenComparing(HerdAgendaRepository.Row::stableId);

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
