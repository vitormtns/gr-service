package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gerenciadorrural.modules.herd.domain.HerdAgendaRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAgendaSource;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardPeriod;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ActivityTotals;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.AttentionSummary;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.HerdSnapshot;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ReproductionPipeline;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import com.gerenciadorrural.modules.herd.domain.PendingWorkType;
import com.gerenciadorrural.modules.herd.domain.HealthTreatmentType;
import com.gerenciadorrural.modules.herd.domain.HealthProcedureCode;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalAction;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadHerdDashboardBrucellosisTest {
  private final TenantId tenant = new TenantId(UUID.randomUUID());
  private final UUID user = UUID.randomUUID();
  private final UUID farm = UUID.randomUUID();
  private final TenantContext context =
      new TenantContext(tenant, user, farm, UUID.randomUUID(), "VIEWER", "ALL_FARMS");
  private final LocalDate today = LocalDate.of(2026, 9, 13);
  private final HerdDashboardRepository repository = mock(HerdDashboardRepository.class);
  private final HerdAgendaRepository agenda = mock(HerdAgendaRepository.class);
  private final HerdManagementRepository herd = mock(HerdManagementRepository.class);
  private ReadHerdAgenda derivedAgenda;
  private ReadHerdDashboard service;

  @BeforeEach
  void setUp() {
    when(repository.snapshot(any(), any(), any(), any(), any()))
        .thenReturn(new HerdSnapshot(0, Map.of(), Map.of(), List.of(), 0));
    when(repository.activity(any(), any(), any(), any())).thenReturn(activity());
    when(repository.attention(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new AttentionSummary(4, 3, 2, 1, 0, 7, 6, 0, 0));
    when(repository.reproductionPipeline(any(), any(), any(), anyInt()))
        .thenReturn(new ReproductionPipeline(0, 0));
    when(repository.activitySeries(any(), any(), any(), any())).thenReturn(List.of());
    when(agenda.page(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of());
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    TenantTransactionExecutor transactions = synchronousTransactions();
    derivedAgenda = new ReadHerdAgenda(transactions, agenda, herd, clock, 90, 14);
    service =
        new ReadHerdDashboard(
            transactions,
            repository,
            derivedAgenda,
            herd,
            clock,
            90,
            14,
            366);
  }

  private HerdManagementRepository.Treatment dose(LocalDate occurredOn) {
    return new HerdManagementRepository.Treatment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        HealthTreatmentType.VACCINATION,
        HealthProcedureCode.BRUCELLOSIS,
        occurredOn,
        null,
        null,
        null,
        null,
        user,
        Instant.now());
  }

  private HerdManagementRepository.BrucellosisPendingCandidate candidate(
      LocalDate birth, HerdManagementRepository.Treatment... doses) {
    UUID id = UUID.randomUUID();
    return new HerdManagementRepository.BrucellosisPendingCandidate(
        id, "B-" + id.toString().substring(0, 4), null, farm, HerdAnimalSex.FEMALE, birth, List.of(doses));
  }

  private void stubCandidates(HerdManagementRepository.BrucellosisPendingCandidate... candidates) {
    when(herd.brucellosisPendingCandidates(tenant, farm, today, null)).thenReturn(List.of(candidates));
  }

  private void stubLegacy(HerdAgendaRepository.Row... rows) {
    when(agenda.count(tenant, farm, today, 90, 14, null, null, null, null, null))
        .thenReturn((long) rows.length);
    when(agenda.page(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenAnswer(invocation -> List.of(rows).subList(0, Math.min(rows.length, invocation.getArgument(10))));
  }

  private HerdAgendaRepository.Row legacy(LocalDate date, String stableId) {
    UUID animal = UUID.randomUUID();
    return new HerdAgendaRepository.Row(
        HerdAgendaSource.DERIVED, HerdPlannerType.WEIGHING.name(), date, stableId, animal,
        "Pesagem pendente", "L-1", "Lua", null, PendingWorkType.WEIGHING_DUE, null, null);
  }

  @Test
  void zeroCountsWithoutClassifiableCandidates() {
    stubCandidates();

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().brucellosisDue()).isZero();
    assertThat(attention.summary().brucellosisWindowMissed()).isZero();
  }

  @Test
  void countsDueWithoutMissed() {
    stubCandidates(candidate(LocalDate.of(2026, 4, 13)));

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().brucellosisDue()).isOne();
    assertThat(attention.summary().brucellosisWindowMissed()).isZero();
  }

  @Test
  void countsMissedWithoutDue() {
    stubCandidates(candidate(LocalDate.of(2025, 9, 13)));

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().brucellosisDue()).isZero();
    assertThat(attention.summary().brucellosisWindowMissed()).isOne();
  }

  @Test
  void countsMixedStates() {
    stubCandidates(
        candidate(LocalDate.of(2026, 4, 13)),
        candidate(LocalDate.of(2026, 5, 13)),
        candidate(LocalDate.of(2026, 6, 13)),
        candidate(LocalDate.of(2025, 9, 13)),
        candidate(LocalDate.of(2025, 10, 13)),
        candidate(LocalDate.of(2026, 4, 13), dose(LocalDate.of(2026, 7, 13))),
        candidate(LocalDate.of(2026, 7, 13)),
        candidate(LocalDate.of(2025, 9, 13), dose(LocalDate.of(2026, 7, 13))));

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().brucellosisDue()).isEqualTo(3);
    assertThat(attention.summary().brucellosisWindowMissed()).isEqualTo(2);
  }

  @Test
  void primaryRecordContributesNothing() {
    stubCandidates(candidate(LocalDate.of(2026, 4, 13), dose(LocalDate.of(2026, 7, 13))));

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().brucellosisDue()).isZero();
    assertThat(attention.summary().brucellosisWindowMissed()).isZero();
  }

  @Test
  void postWindowRecordContributesNothing() {
    stubCandidates(candidate(LocalDate.of(2025, 9, 13), dose(LocalDate.of(2026, 7, 13))));

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().brucellosisDue()).isZero();
    assertThat(attention.summary().brucellosisWindowMissed()).isZero();
  }

  @Test
  void legacyCountsPassThroughUnchanged() {
    stubCandidates(candidate(LocalDate.of(2026, 4, 13)));

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().vaccinationDue()).isEqualTo(4);
    assertThat(attention.summary().dewormingDue()).isEqualTo(3);
    assertThat(attention.summary().weighingDue()).isEqualTo(2);
    assertThat(attention.summary().calvingUpcoming()).isOne();
    assertThat(attention.summary().calvingOverdue()).isZero();
    assertThat(attention.summary().plannerOpen()).isEqualTo(7);
    assertThat(attention.summary().plannerOverdue()).isEqualTo(6);
  }

  @Test
  void genericVaccinationAndBrucellosisCountCoexist() {
    stubCandidates(candidate(LocalDate.of(2026, 4, 13)));

    ReadHerdDashboard.Attention attention = service.attention(context, 5);

    assertThat(attention.summary().vaccinationDue()).isEqualTo(4);
    assertThat(attention.summary().brucellosisDue()).isOne();
  }

  @Test
  void countsAndPreviewUseSameReferenceDateInTwoBulkCandidateQueries() {
    stubCandidates(candidate(LocalDate.of(2026, 4, 13)));

    service.attention(context, 5);

    verify(herd, times(2)).brucellosisPendingCandidates(tenant, farm, today, null);
  }

  @Test
  void dueAppearsInPreviewWithSameAgendaItemAndStableId() {
    var due = candidate(LocalDate.of(2026, 4, 13));
    stubCandidates(due);

    var preview = service.attention(context, 5).preview();
    var agendaItem = derivedAgenda.page(context, null, null, null, null, null, 0, 5).items().get(0);

    assertThat(preview).singleElement().satisfies(item -> {
      assertThat(item.pendingWorkType()).isEqualTo(PendingWorkType.BRUCELLOSIS_DUE);
      assertThat(item.stableId()).isEqualTo(agendaItem.stableId());
      assertThat(item.source()).isEqualTo(agendaItem.source());
      assertThat(item.kind()).isEqualTo(agendaItem.kind());
      assertThat(item.operationalDate()).isEqualTo(agendaItem.operationalDate());
      assertThat(item.summary()).isEqualTo(agendaItem.summary());
    });
  }

  @Test
  void missedAppearsInPreviewWhenItIsFirst() {
    stubCandidates(candidate(LocalDate.of(2025, 9, 13)));

    var attention = service.attention(context, 1);

    assertThat(attention.summary().brucellosisWindowMissed()).isOne();
    assertThat(attention.preview()).singleElement()
        .extracting(ReadHerdDashboard.AttentionItem::pendingWorkType)
        .isEqualTo(PendingWorkType.BRUCELLOSIS_WINDOW_MISSED);
  }

  @Test
  void previewUsesFirstItemsOfGloballyOrderedLegacyAndBrucAgenda() {
    var first = legacy(LocalDate.of(2026, 6, 1), "legacy-first");
    var last = legacy(LocalDate.of(2026, 6, 20), "legacy-last");
    stubLegacy(first, last);
    stubCandidates(candidate(LocalDate.of(2025, 9, 13)));

    var attention = service.attention(context, 2);
    var agendaItems = derivedAgenda.page(context, null, null, null, null, null, 0, 2).items();

    assertThat(attention.preview()).extracting(ReadHerdDashboard.AttentionItem::stableId)
        .containsExactlyElementsOf(agendaItems.stream().map(ReadHerdAgenda.Item::stableId).toList());
    assertThat(attention.preview()).extracting(ReadHerdDashboard.AttentionItem::pendingWorkType)
        .containsExactly(PendingWorkType.WEIGHING_DUE, PendingWorkType.BRUCELLOSIS_WINDOW_MISSED);
    assertThat(attention.preview()).extracting(ReadHerdDashboard.AttentionItem::operationalDate)
        .containsExactly(first.operationalDate(), LocalDate.of(2026, 6, 13));
  }

  @Test
  void brucAtPositionAfterPreviewSizeKeepsCountButNotPreviewItem() {
    var first = legacy(LocalDate.of(2026, 6, 1), "legacy-first");
    stubLegacy(first);
    stubCandidates(candidate(LocalDate.of(2026, 4, 13)));

    var attention = service.attention(context, 1);

    assertThat(attention.summary().brucellosisDue()).isOne();
    assertThat(attention.preview()).singleElement()
        .extracting(ReadHerdDashboard.AttentionItem::stableId)
        .isEqualTo(first.stableId());
  }

  @Test
  void legacyOnlyPreviewKeepsEveryField() {
    var row = legacy(LocalDate.of(2026, 9, 1), "legacy-only");
    stubLegacy(row);
    stubCandidates();

    var attention = service.attention(context, 1);

    assertThat(attention.preview()).containsExactly(new ReadHerdDashboard.AttentionItem(
        row.source(), row.kind(), row.operationalDate(), row.stableId(), row.summary(),
        new ReadHerdDashboard.AnimalReference(row.animalId(), row.identification(), row.name()),
        row.plannerItemId(), row.pendingWorkType(), row.pregnancyId(), row.status()));
  }

  @Test
  void overviewSharesDerivedCounts() {
    stubCandidates(candidate(LocalDate.of(2026, 4, 13)), candidate(LocalDate.of(2025, 9, 13)));

    var overview = service.overview(context, HerdDashboardPeriod.TODAY, null, null, null, null, null);

    assertThat(overview.attention().brucellosisDue()).isOne();
    assertThat(overview.attention().brucellosisWindowMissed()).isOne();
    verify(herd, times(1)).brucellosisPendingCandidates(tenant, farm, today, null);
    verifyNoInteractions(agenda);
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
