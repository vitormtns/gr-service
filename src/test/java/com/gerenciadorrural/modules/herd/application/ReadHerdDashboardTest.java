package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerenciadorrural.modules.herd.domain.HerdAgendaRepository;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardPeriod;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ActivityTotals;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.AttentionSummary;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.HerdSnapshot;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ReproductionPipeline;
import com.gerenciadorrural.modules.herd.domain.HerdReportCategory;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalAction;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadHerdDashboardTest {
  private final TenantContext context =
      new TenantContext(
          new TenantId(UUID.randomUUID()),
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID(),
          "VIEWER",
          "ALL_FARMS");
  private final HerdDashboardRepository repository = mock(HerdDashboardRepository.class);
  private final HerdAgendaRepository agenda = mock(HerdAgendaRepository.class);
  private ReadHerdDashboard service;

  @BeforeEach
  void setUp() {
    when(repository.snapshot(any(), any(), any(), any(), any()))
        .thenReturn(new HerdSnapshot(0, Map.of(), Map.of(), List.of(), 0));
    when(repository.activity(any(), any(), any(), any())).thenReturn(activity());
    when(repository.attention(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new AttentionSummary(0, 0, 0, 0, 0, 0, 0));
    when(repository.reproductionPipeline(any(), any(), any(), anyInt()))
        .thenReturn(new ReproductionPipeline(0, 0));
    when(repository.activitySeries(any(), any(), any(), any())).thenReturn(List.of());
    when(agenda.page(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of());
    service =
        new ReadHerdDashboard(
            synchronousTransactions(),
            repository,
            agenda,
            Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC),
            90,
            14,
            366);
  }

  @Test
  void resolvesAllPeriodBoundariesFromTheInjectedClock() {
    assertThat(service.activity(context, HerdDashboardPeriod.TODAY, null, null).period())
        .extracting("from", "to")
        .containsExactly(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13));
    assertThat(service.activity(context, HerdDashboardPeriod.LAST_7_DAYS, null, null).period())
        .extracting("from", "to")
        .containsExactly(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));
    assertThat(service.activity(context, HerdDashboardPeriod.LAST_30_DAYS, null, null).period())
        .extracting("from", "to")
        .containsExactly(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 13));
    assertThat(
            service
                .activity(
                    context,
                    HerdDashboardPeriod.CUSTOM,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 2, 1))
                .period())
        .extracting("from", "to")
        .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));
  }

  @Test
  void rejectsAmbiguousInvalidAndExcessivePeriodsAndInvalidPreview() {
    assertThatThrownBy(
            () -> service.activity(context, HerdDashboardPeriod.CUSTOM, null, null))
        .isInstanceOf(HerdDashboardQueryInvalidException.class);
    assertThatThrownBy(
            () ->
                service.activity(
                    context,
                    HerdDashboardPeriod.TODAY,
                    LocalDate.of(2026, 1, 1),
                    null))
        .isInstanceOf(HerdDashboardQueryInvalidException.class);
    assertThatThrownBy(
            () ->
                service.activity(
                    context,
                    HerdDashboardPeriod.CUSTOM,
                    LocalDate.of(2026, 2, 1),
                    LocalDate.of(2026, 1, 1)))
        .isInstanceOf(HerdDashboardQueryInvalidException.class);
    assertThatThrownBy(
            () ->
                service.activity(
                    context,
                    HerdDashboardPeriod.CUSTOM,
                    LocalDate.of(2010, 1, 1),
                    LocalDate.of(2026, 1, 1)))
        .isInstanceOf(HerdDashboardQueryInvalidException.class);
    assertThatThrownBy(() -> service.attention(context, 11))
        .isInstanceOf(HerdDashboardQueryInvalidException.class);
  }

  @Test
  void calculatesAuditableCoverageForEmptyCoveredPartialAndDueHerds() {
    assertThat(coverage(0, 0)).isEqualByComparingTo("0.00");
    assertThat(coverage(4, 0)).isEqualByComparingTo("100.00");
    assertThat(coverage(4, 1)).isEqualByComparingTo("75.00");
    assertThat(coverage(4, 4)).isEqualByComparingTo("0.00");
  }

  @Test
  void rejectsRolesOutsideTheReadContract() {
    TenantContext forbidden =
        new TenantContext(
            context.tenantId(),
            context.userId(),
            context.farmId(),
            context.membershipId(),
            "AUDITOR",
            context.farmScopeMode());
    assertThatThrownBy(
            () -> service.overview(forbidden, null, null, null, null, null, null))
        .isInstanceOf(HerdDashboardForbiddenException.class);
  }

  @Test
  void permitsEveryReadRole() {
    for (String role : List.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER")) {
      TenantContext allowed =
          new TenantContext(
              context.tenantId(),
              context.userId(),
              context.farmId(),
              context.membershipId(),
              role,
              context.farmScopeMode());
      assertThat(service.activity(allowed, HerdDashboardPeriod.TODAY, null, null)).isNotNull();
      assertThat(service.attention(allowed, 5)).isNotNull();
    }
  }

  @Test
  void keepsFilteredSnapshotSeparateFromFarmWideInsights() {
    HerdSnapshot filtered = new HerdSnapshot(1, Map.of(), Map.of(), List.of(), 0);
    HerdSnapshot complete = new HerdSnapshot(4, Map.of(), Map.of(), List.of(), 0);
    when(repository.snapshot(any(), any(), any(), any(), any()))
        .thenReturn(filtered)
        .thenReturn(complete);

    var overview =
        service.overview(
            context,
            HerdDashboardPeriod.TODAY,
            null,
            null,
            HerdReportCategory.UNCLASSIFIED,
            null,
            null);

    assertThat(overview.herdSnapshot().activeAnimals()).isOne();
    assertThat(overview.insights().weighingCoverage().totalEligibleAnimals()).isEqualTo(4);
    verify(repository).snapshot(context.tenantId(), context.farmId(), null, null, null);
  }

  private BigDecimal coverage(long active, long due) {
    when(repository.snapshot(any(), any(), any(), any(), any()))
        .thenReturn(new HerdSnapshot(active, Map.of(), Map.of(), List.of(), 0));
    when(repository.attention(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new AttentionSummary(0, 0, due, 0, 0, 0, 0));
    return service
        .overview(context, HerdDashboardPeriod.TODAY, null, null, null, null, null)
        .insights()
        .weighingCoverage()
        .coveragePercentage();
  }

  private static ActivityTotals activity() {
    return new ActivityTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private TenantTransactionExecutor synchronousTransactions() {
    return new TenantTransactionExecutor() {
      @Override
      public <T> T execute(TenantContext actual, TenantTransactionalOperation<T> operation) {
        assertThat(actual).isNotNull();
        return operation.execute();
      }

      @Override
      public void execute(TenantContext actual, TenantTransactionalAction action) {
        assertThat(actual).isNotNull();
        action.execute();
      }
    };
  }
}
