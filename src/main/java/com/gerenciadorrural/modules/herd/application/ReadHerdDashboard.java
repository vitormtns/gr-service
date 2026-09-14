package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAgendaRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAgendaSource;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardPeriod;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ActivityBucket;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ActivityTotals;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.AttentionSummary;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.HerdSnapshot;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ReproductionPipeline;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerStatus;
import com.gerenciadorrural.modules.herd.domain.HerdReportCategory;
import com.gerenciadorrural.modules.herd.domain.PendingWorkType;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReadHerdDashboard {
  private static final Set<String> READ_ROLES =
      Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER");

  private final TenantTransactionExecutor transactions;
  private final HerdDashboardRepository dashboard;
  private final HerdAgendaRepository agenda;
  private final Clock clock;
  private final int weighingDueDays;
  private final int calvingUpcomingDays;
  private final int maximumCustomRangeDays;

  public ReadHerdDashboard(
      TenantTransactionExecutor transactions,
      HerdDashboardRepository dashboard,
      HerdAgendaRepository agenda,
      Clock clock,
      @Value("${herd.pending-work.weighing-due-days:90}") int weighingDueDays,
      @Value("${herd.pending-work.calving-upcoming-days:14}") int calvingUpcomingDays,
      @Value("${herd.dashboard.maximum-custom-range-days:366}") int maximumCustomRangeDays) {
    this.transactions = transactions;
    this.dashboard = dashboard;
    this.agenda = agenda;
    this.clock = clock;
    this.weighingDueDays = weighingDueDays;
    this.calvingUpcomingDays = calvingUpcomingDays;
    this.maximumCustomRangeDays = maximumCustomRangeDays;
  }

  public Overview overview(
      TenantContext context,
      HerdDashboardPeriod period,
      LocalDate from,
      LocalDate to,
      HerdReportCategory category,
      HerdAnimalSex sex,
      UUID paddockId) {
    ResolvedPeriod resolved = validate(context, period, from, to);
    return transactions.execute(
        context,
        () -> {
          HerdSnapshot snapshot =
              dashboard.snapshot(context.tenantId(), context.farmId(), category, sex, paddockId);
          HerdSnapshot insightSnapshot =
              category == null && sex == null && paddockId == null
                  ? snapshot
                  : dashboard.snapshot(context.tenantId(), context.farmId(), null, null, null);
          ActivityTotals activity =
              dashboard.activity(
                  context.tenantId(), context.farmId(), resolved.from(), resolved.to());
          AttentionSummary attention = currentAttention(context, resolved.referenceDate());
          ReproductionPipeline pipeline =
              dashboard.reproductionPipeline(
                  context.tenantId(),
                  context.farmId(),
                  resolved.referenceDate(),
                  calvingUpcomingDays);
          return new Overview(
              resolved,
              snapshot,
              activity,
              new OverviewAttention(
                  attention.vaccinationDue(),
                  attention.dewormingDue(),
                  attention.weighingDue(),
                  attention.calvingUpcoming(),
                  attention.calvingOverdue(),
                  attention.plannerOpen(),
                  attention.plannerOverdue()),
              insights(insightSnapshot, activity, attention, pipeline, resolved));
        });
  }

  public Activity activity(
      TenantContext context, HerdDashboardPeriod period, LocalDate from, LocalDate to) {
    ResolvedPeriod resolved = validate(context, period, from, to);
    return transactions.execute(
        context,
        () ->
            new Activity(
                resolved,
                dashboard.activity(
                    context.tenantId(), context.farmId(), resolved.from(), resolved.to()),
                dashboard.activitySeries(
                    context.tenantId(), context.farmId(), resolved.from(), resolved.to())));
  }

  public Attention attention(TenantContext context, int previewSize) {
    authorize(context);
    if (previewSize < 1 || previewSize > 10) {
      throw new HerdDashboardQueryInvalidException();
    }
    LocalDate referenceDate = LocalDate.now(clock);
    return transactions.execute(
        context,
        () -> {
          AttentionSummary summary = currentAttention(context, referenceDate);
          List<AttentionItem> preview =
              agenda
                  .page(
                      context.tenantId(),
                      context.farmId(),
                      referenceDate,
                      weighingDueDays,
                      calvingUpcomingDays,
                      null,
                      null,
                      null,
                      null,
                      null,
                      previewSize,
                      0)
                  .stream()
                  .map(ReadHerdDashboard::item)
                  .toList();
          return new Attention(referenceDate, summary, preview);
        });
  }

  private AttentionSummary currentAttention(TenantContext context, LocalDate referenceDate) {
    return dashboard.attention(
        context.tenantId(),
        context.farmId(),
        referenceDate,
        weighingDueDays,
        calvingUpcomingDays);
  }

  private Insights insights(
      HerdSnapshot snapshot,
      ActivityTotals activity,
      AttentionSummary attention,
      ReproductionPipeline pipeline,
      ResolvedPeriod period) {
    long eligible = snapshot.activeAnimals();
    long covered = Math.max(0, eligible - attention.weighingDue());
    BigDecimal coverage =
        eligible == 0
            ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(covered)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(eligible), 2, RoundingMode.HALF_UP);
    return new Insights(
        new WeighingCoverageInsight(
            InsightType.WEIGHING_COVERAGE, covered, eligible, coverage),
        new HealthDueInsight(
            InsightType.HEALTH_DUE,
            attention.vaccinationDue(),
            attention.dewormingDue()),
        new ReproductionPipelineInsight(
            InsightType.REPRODUCTION_PIPELINE,
            pipeline.openPossiblePregnancies(),
            pipeline.openConfirmedPregnancies(),
            attention.calvingUpcoming(),
            attention.calvingOverdue(),
            activity.calvings(),
            activity.calvesBorn()),
        new CalvingAttentionInsight(
            InsightType.CALVING_ATTENTION,
            attention.calvingUpcoming(),
            attention.calvingOverdue()),
        new PlannerExecutionInsight(
            InsightType.PLANNER_EXECUTION,
            activity.plannerCompleted(),
            activity.plannerCancelled(),
            attention.plannerOpen(),
            attention.plannerOverdue()),
        new HerdActivityInsight(
            InsightType.HERD_ACTIVITY,
            activity.births(),
            activity.deaths(),
            activity.sales(),
            activity.movements(),
            activity.transfersIn(),
            activity.transfersOut(),
            period.from(),
            period.to()));
  }

  private ResolvedPeriod validate(
      TenantContext context, HerdDashboardPeriod requested, LocalDate from, LocalDate to) {
    authorize(context);
    HerdDashboardPeriod period =
        requested == null ? HerdDashboardPeriod.LAST_30_DAYS : requested;
    LocalDate referenceDate = LocalDate.now(clock);
    LocalDate effectiveFrom;
    LocalDate effectiveTo;
    if (period == HerdDashboardPeriod.CUSTOM) {
      if (from == null || to == null) {
        throw new HerdDashboardQueryInvalidException();
      }
      effectiveFrom = from;
      effectiveTo = to;
    } else {
      if (from != null || to != null) {
        throw new HerdDashboardQueryInvalidException();
      }
      effectiveTo = referenceDate;
      effectiveFrom =
          switch (period) {
            case TODAY -> referenceDate;
            case LAST_7_DAYS -> referenceDate.minusDays(6);
            case LAST_30_DAYS -> referenceDate.minusDays(29);
            case CUSTOM -> throw new IllegalStateException("Período CUSTOM já foi tratado");
          };
    }
    long days = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
    if (days < 0 || days >= maximumCustomRangeDays) {
      throw new HerdDashboardQueryInvalidException();
    }
    return new ResolvedPeriod(period, effectiveFrom, effectiveTo, referenceDate);
  }

  private static void authorize(TenantContext context) {
    if (!READ_ROLES.contains(context.role())) {
      throw new HerdDashboardForbiddenException();
    }
  }

  private static AttentionItem item(HerdAgendaRepository.Row row) {
    AnimalReference animal =
        row.animalId() == null
            ? null
            : new AnimalReference(row.animalId(), row.identification(), row.name());
    return new AttentionItem(
        row.source(),
        row.kind(),
        row.operationalDate(),
        row.stableId(),
        row.summary(),
        animal,
        row.plannerItemId(),
        row.pendingWorkType(),
        row.pregnancyId(),
        row.status());
  }

  public enum InsightType {
    WEIGHING_COVERAGE,
    HEALTH_DUE,
    REPRODUCTION_PIPELINE,
    CALVING_ATTENTION,
    PLANNER_EXECUTION,
    HERD_ACTIVITY
  }

  public record ResolvedPeriod(
      HerdDashboardPeriod period, LocalDate from, LocalDate to, LocalDate referenceDate) {}

  public record Overview(
      ResolvedPeriod period,
      HerdSnapshot herdSnapshot,
      ActivityTotals periodActivity,
      OverviewAttention attention,
      Insights insights) {}

  public record OverviewAttention(
      long vaccinationDue,
      long dewormingDue,
      long weighingDue,
      long calvingUpcoming,
      long calvingOverdue,
      long openPlannerItems,
      long overduePlannerItems) {}

  public record Activity(
      ResolvedPeriod period, ActivityTotals totals, List<ActivityBucket> series) {}

  public record Attention(
      LocalDate referenceDate, AttentionSummary summary, List<AttentionItem> preview) {}

  public record AnimalReference(UUID id, String identification, String name) {}

  public record AttentionItem(
      HerdAgendaSource source,
      String kind,
      LocalDate operationalDate,
      String stableId,
      String summary,
      AnimalReference animal,
      UUID plannerItemId,
      PendingWorkType pendingWorkType,
      UUID pregnancyId,
      HerdPlannerStatus status) {}

  public record Insights(
      WeighingCoverageInsight weighingCoverage,
      HealthDueInsight healthDue,
      ReproductionPipelineInsight reproductionPipeline,
      CalvingAttentionInsight calvingAttention,
      PlannerExecutionInsight plannerExecution,
      HerdActivityInsight herdActivity) {}

  public record WeighingCoverageInsight(
      InsightType type,
      long coveredAnimals,
      long totalEligibleAnimals,
      BigDecimal coveragePercentage) {}

  public record HealthDueInsight(
      InsightType type, long vaccinationDue, long dewormingDue) {}

  public record ReproductionPipelineInsight(
      InsightType type,
      long openPossiblePregnancies,
      long openConfirmedPregnancies,
      long upcomingCalvings,
      long overdueCalvings,
      long calvingsInPeriod,
      long calvesBornInPeriod) {}

  public record CalvingAttentionInsight(
      InsightType type, long upcomingCalvings, long overdueCalvings) {}

  public record PlannerExecutionInsight(
      InsightType type, long completed, long cancelled, long currentlyOpen, long overdueOpen) {}

  public record HerdActivityInsight(
      InsightType type,
      long births,
      long deaths,
      long sales,
      long movements,
      long transfersIn,
      long transfersOut,
      LocalDate from,
      LocalDate to) {}
}
