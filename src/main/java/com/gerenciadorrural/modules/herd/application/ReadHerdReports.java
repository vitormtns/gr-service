package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.domain.HerdReportRepository.*;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReadHerdReports {
  private static final Set<String> READ_ROLES =
      Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER");

  private final TenantTransactionExecutor transactions;
  private final HerdReportRepository reports;
  private final Clock clock;
  private final int defaultRangeDays;
  private final int maximumRangeDays;

  public ReadHerdReports(
      TenantTransactionExecutor transactions,
      HerdReportRepository reports,
      Clock clock,
      @Value("${herd.reports.default-range-days:365}") int defaultRangeDays,
      @Value("${herd.reports.maximum-range-days:3650}") int maximumRangeDays) {
    if (defaultRangeDays < 1
        || maximumRangeDays < defaultRangeDays
        || maximumRangeDays > 36525) {
      throw new IllegalArgumentException("As janelas de relatórios são inválidas");
    }
    this.transactions = transactions;
    this.reports = reports;
    this.clock = clock;
    this.defaultRangeDays = defaultRangeDays;
    this.maximumRangeDays = maximumRangeDays;
  }

  public AgeSexBalance currentAgeSexBalance(TenantContext context, LocalDate referenceDate) {
    validateRole(context);
    LocalDate today = LocalDate.now(clock);
    LocalDate reference = referenceDate == null ? today : referenceDate;
    if (reference.isAfter(today)) {
      throw new HerdReportQueryInvalidException();
    }
    return transactions.execute(context, () -> {
      Map<AgeBand, Map<HerdAnimalSex, Long>> counts = new EnumMap<>(AgeBand.class);
      for (AgeBand band : AgeBand.values()) {
        Map<HerdAnimalSex, Long> bySex = new EnumMap<>(HerdAnimalSex.class);
        for (HerdAnimalSex sex : HerdAnimalSex.values()) {
          bySex.put(sex, 0L);
        }
        counts.put(band, bySex);
      }
      long total = 0;
      long unknownBirthDate = 0;
      for (AgeSexCount row : reports.currentAgeSexCounts(context.tenantId(), context.farmId())) {
        total += row.count();
        if (row.birthDate() == null || row.birthDate().isAfter(reference)) {
          unknownBirthDate += row.count();
        } else {
          Map<HerdAnimalSex, Long> bySex = counts.get(AgePolicy.classify(row.birthDate(), reference));
          bySex.merge(row.sex(), row.count(), Long::sum);
        }
      }
      List<AgeSexCell> cells = new ArrayList<>();
      for (AgeBand band : AgeBand.values()) {
        for (HerdAnimalSex sex : HerdAnimalSex.values()) {
          cells.add(new AgeSexCell(band, sex, counts.get(band).get(sex)));
        }
      }
      return new AgeSexBalance(reference, "CURRENT_STATE_AGED_AT_REFERENCE", total,
          unknownBirthDate, List.copyOf(cells));
    });
  }

  public PeriodReconciliation periodReconciliation(TenantContext context, LocalDate from,
      LocalDate to) {
    validateRole(context);
    if (from == null || to == null || to.isAfter(LocalDate.now(clock)))
      throw new HerdReportQueryInvalidException();
    DateRange range = validate(context, from, to, 0, 1);
    return transactions.execute(context, () -> new PeriodReconciliation(range.from(), range.to(),
        "RECORDED_FARM_EVENT_LEDGER", reports.eventLedger(context.tenantId(), context.farmId(),
        range.from(), range.to())));
  }

  public Page<HerdPositionSummary, HerdPositionItem> herdPosition(
      TenantContext context,
      HerdReportCategory category,
      HerdAnimalSex sex,
      UUID paddockId,
      int page,
      int size) {
    validate(context, page, size);
    return execute(
        context,
        page,
        size,
        () ->
            reports.herdPosition(
                context.tenantId(),
                context.farmId(),
                category,
                sex,
                paddockId,
                size,
                offset(page, size)));
  }

  public Page<LifecycleSummary, LifecycleItem> lifecycle(
      TenantContext context,
      LocalDate from,
      LocalDate to,
      LifecycleEvent event,
      UUID animalId,
      int page,
      int size) {
    DateRange range = validate(context, from, to, page, size);
    return execute(
        context,
        page,
        size,
        () ->
            reports.lifecycle(
                context.tenantId(),
                context.farmId(),
                range.from(),
                range.to(),
                event,
                animalId,
                size,
                offset(page, size)));
  }

  public Page<MovementSummary, MovementItem> movements(
      TenantContext context,
      LocalDate from,
      LocalDate to,
      UUID animalId,
      UUID sourcePaddockId,
      UUID destinationPaddockId,
      int page,
      int size) {
    DateRange range = validate(context, from, to, page, size);
    return execute(
        context,
        page,
        size,
        () ->
            reports.movements(
                context.tenantId(),
                context.farmId(),
                range.from(),
                range.to(),
                animalId,
                sourcePaddockId,
                destinationPaddockId,
                size,
                offset(page, size)));
  }

  public Page<TransferSummary, TransferItem> transfers(
      TenantContext context,
      LocalDate from,
      LocalDate to,
      TransferDirection direction,
      UUID animalId,
      int page,
      int size) {
    DateRange range = validate(context, from, to, page, size);
    TransferDirection selected = direction == null ? TransferDirection.ALL : direction;
    return execute(
        context,
        page,
        size,
        () ->
            reports.transfers(
                context.tenantId(),
                context.farmId(),
                range.from(),
                range.to(),
                selected,
                animalId,
                size,
                offset(page, size)));
  }

  public Page<WeightSummary, WeightItem> weights(
      TenantContext context,
      LocalDate from,
      LocalDate to,
      UUID animalId,
      HerdReportCategory category,
      int page,
      int size) {
    DateRange range = validate(context, from, to, page, size);
    return execute(
        context,
        page,
        size,
        () ->
            reports.weights(
                context.tenantId(),
                context.farmId(),
                range.from(),
                range.to(),
                animalId,
                category,
                size,
                offset(page, size)));
  }

  public Page<HealthSummary, HealthItem> health(
      TenantContext context,
      LocalDate from,
      LocalDate to,
      HealthTreatmentType treatmentType,
      UUID animalId,
      int page,
      int size) {
    DateRange range = validate(context, from, to, page, size);
    return execute(
        context,
        page,
        size,
        () ->
            reports.health(
                context.tenantId(),
                context.farmId(),
                range.from(),
                range.to(),
                treatmentType,
                animalId,
                size,
                offset(page, size)));
  }

  public Page<ReproductionSummary, ReproductionItem> reproduction(
      TenantContext context,
      LocalDate from,
      LocalDate to,
      UUID motherId,
      ReproductionServiceType serviceType,
      PregnancyStatus pregnancyStatus,
      int page,
      int size) {
    DateRange range = validate(context, from, to, page, size);
    return execute(
        context,
        page,
        size,
        () ->
            reports.reproduction(
                context.tenantId(),
                context.farmId(),
                range.from(),
                range.to(),
                motherId,
                serviceType,
                pregnancyStatus,
                size,
                offset(page, size)));
  }

  public Page<PlannerSummary, PlannerItem> planner(
      TenantContext context,
      LocalDate from,
      LocalDate to,
      HerdPlannerStatus status,
      HerdPlannerType type,
      UUID animalId,
      int page,
      int size) {
    DateRange range = validate(context, from, to, page, size);
    return execute(
        context,
        page,
        size,
        () ->
            reports.planner(
                context.tenantId(),
                context.farmId(),
                range.from(),
                range.to(),
                LocalDate.now(clock),
                status,
                type,
                animalId,
                size,
                offset(page, size)));
  }

  private <S, I> Page<S, I> execute(
      TenantContext context,
      int page,
      int size,
      Supplier<ReportPage<S, I>> operation) {
    return transactions.execute(
        context,
        () -> {
          ReportPage<S, I> result = operation.get();
          return new Page<>(
              result.summary(),
              result.items(),
              page,
              size,
              result.totalElements(),
              pages(result.totalElements(), size));
        });
  }

  private DateRange validate(
      TenantContext context, LocalDate from, LocalDate to, int page, int size) {
    validate(context, page, size);
    LocalDate today = LocalDate.now(clock);
    LocalDate effectiveTo = to == null ? today : to;
    LocalDate effectiveFrom =
        from == null ? effectiveTo.minusDays(defaultRangeDays - 1L) : from;
    long days = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
    if (days < 0 || days >= maximumRangeDays) {
      throw new HerdReportQueryInvalidException();
    }
    return new DateRange(effectiveFrom, effectiveTo);
  }

  private static void validate(TenantContext context, int page, int size) {
    validateRole(context);
    if (page < 0 || size < 1 || size > 100 || offset(page, size) > Integer.MAX_VALUE) {
      throw new HerdReportQueryInvalidException();
    }
  }

  private static void validateRole(TenantContext context) {
    if (!READ_ROLES.contains(context.role())) {
      throw new HerdReportForbiddenException();
    }
  }

  private static long offset(int page, int size) {
    return (long) page * size;
  }

  private static int pages(long total, int size) {
    return Math.toIntExact((total + size - 1) / size);
  }

  private record DateRange(LocalDate from, LocalDate to) {}

  public record Page<S, I>(
      S summary, List<I> items, int page, int size, long totalElements, int totalPages) {}

  public record AgeSexCell(AgeBand ageBand, HerdAnimalSex sex, long count) {}

  public record AgeSexBalance(LocalDate referenceDate, String positionSemantics,
      long totalActiveAnimals, long unknownBirthDate, List<AgeSexCell> cells) {}

  public record PeriodReconciliation(LocalDate from, LocalDate to, String positionSemantics,
      EventLedger balance) {}
}
