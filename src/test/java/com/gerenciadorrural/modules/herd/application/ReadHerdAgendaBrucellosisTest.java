package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReadHerdAgendaBrucellosisTest {

    TenantTransactionExecutor tx = mock();
    HerdAgendaRepository agenda = mock();
    HerdManagementRepository herd = mock();

    TenantId tenant = new TenantId(UUID.randomUUID());
    UUID user = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    TenantContext c = new TenantContext(tenant, user, farm, UUID.randomUUID(), "OPERATOR", "ALL_FARMS");

    LocalDate today = LocalDate.of(2026, 9, 22);
    ReadHerdAgenda service;

    @BeforeEach
    void setup() {
        doAnswer(x -> ((TenantTransactionalOperation<?>) x.getArgument(1)).execute())
                .when(tx).execute(any(), org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
        service = new ReadHerdAgenda(tx, agenda, herd,
                Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC), 90, 14);
    }

    private HerdManagementRepository.Treatment dose(LocalDate occurredOn) {
        return new HerdManagementRepository.Treatment(UUID.randomUUID(), UUID.randomUUID(),
                HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                occurredOn, null, null, null, null, user, Instant.now());
    }

    private HerdManagementRepository.BrucellosisPendingCandidate candidate(
            LocalDate birth, HerdManagementRepository.Treatment... doses) {
        UUID id = UUID.randomUUID();
        return new HerdManagementRepository.BrucellosisPendingCandidate(id, "B-" + id.toString().substring(0, 4),
                null, farm, HerdAnimalSex.FEMALE, birth, List.of(doses));
    }

    private void stubCandidates(HerdManagementRepository.BrucellosisPendingCandidate... candidates) {
        when(herd.brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), any()))
                .thenReturn(List.of(candidates));
    }

    private HerdAgendaRepository.Row legacyRow(
            HerdAgendaSource source, String kind, LocalDate date, String stableId, UUID animalId) {
        return new HerdAgendaRepository.Row(source, kind, date, stableId, animalId, "Resumo",
                "L-" + animalId.toString().substring(0, 4), null,
                source == HerdAgendaSource.MANUAL ? UUID.randomUUID() : null,
                source == HerdAgendaSource.DERIVED ? PendingWorkType.VACCINATION_DUE : null,
                null, source == HerdAgendaSource.MANUAL ? HerdPlannerStatus.OPEN : null);
    }

    private void stubLegacy(List<HerdAgendaRepository.Row> rows, long total) {
        when(agenda.page(eq(tenant), eq(farm), eq(today), eq(90), eq(14), any(), any(), any(), any(), any(),
                anyInt(), anyLong())).thenReturn(rows);
        when(agenda.count(eq(tenant), eq(farm), eq(today), eq(90), eq(14), any(), any(), any(), any(), any()))
                .thenReturn(total);
    }

    @Test
    void todayIncludingOverdueKeepsMissedBrucellosisAndUsesSameUnboundedLowerFilterForPageAndCount() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(today.minusMonths(10)));
        var result = service.page(c, null, null, null, today, today, 0, 20, true);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.pendingWorkType()).isEqualTo(PendingWorkType.BRUCELLOSIS_WINDOW_MISSED);
            assertThat(item.operationalDate()).isBefore(today);
            assertThat(item.displayOn()).isEqualTo(today);
        });
        verify(agenda).count(tenant, farm, today, 90, 14, null, null, null, null, today);
        verify(agenda).page(tenant, farm, today, 90, 14, null, null, null, null, today, 0, 0L);
        assertThat(service.page(c, null, null, null, today, today, 0, 20).items()).isEmpty();
    }

    @Test
    void overdueExpansionCannotBeAppliedToPastOrUnboundedRange() {
        assertThatThrownBy(() -> service.page(c, null, null, null, today.minusDays(1), today, 0, 20, true))
                .isInstanceOf(HerdPlannerExceptions.QueryInvalid.class);
        assertThatThrownBy(() -> service.page(c, null, null, null, today, null, 0, 20, true))
                .isInstanceOf(HerdPlannerExceptions.QueryInvalid.class);
        verifyNoInteractions(agenda, herd);
    }

    @Test
    void dueInsideFutureRangeProducesDerivedVaccinationItem() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 1, 15)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.source()).isEqualTo(HerdAgendaSource.DERIVED);
            assertThat(item.kind()).isEqualTo("VACCINATION");
            assertThat(item.operationalDate()).isEqualTo(LocalDate.of(2026, 10, 15));
            assertThat(item.stableId()).endsWith(":BRUCELLOSIS_DUE");
            assertThat(item.summary()).isEqualTo("Brucelose na janela de vacinação");
            assertThat(item.pendingWorkType()).isEqualTo(PendingWorkType.BRUCELLOSIS_DUE);
            assertThat(item.pregnancyId()).isNull();
            assertThat(item.plannerItemId()).isNull();
            assertThat(item.status()).isNull();
        });
    }

    @Test
    void dueOutsideRangeProducesNothing() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 1, 15)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void missedInsidePastRangeProducesSingleItem() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2025, 11, 15)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.operationalDate()).isEqualTo(LocalDate.of(2026, 8, 15));
            assertThat(item.stableId()).endsWith(":BRUCELLOSIS_WINDOW_MISSED");
            assertThat(item.summary()).isEqualTo("Janela de brucelose perdida");
            assertThat(item.pendingWorkType()).isEqualTo(PendingWorkType.BRUCELLOSIS_WINDOW_MISSED);
        });
    }

    @Test
    void missedIsNotRepeatedInCurrentRange() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2025, 11, 15)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0, 20);

        assertThat(result.totalElements()).isZero();
    }

    @Test
    void noItemWhenPrimaryRecorded() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 1, 15), dose(LocalDate.of(2026, 4, 15))));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isZero();
    }

    @Test
    void noItemForPostWindowRecord() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2025, 9, 22), dose(LocalDate.of(2026, 7, 22))));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isZero();
    }

    @Test
    void noItemBeforeWindow() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 7, 22)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isZero();
    }

    @Test
    void operationalDateUsesReachesMonthsOnForClampedBirth() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 5, 31)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null,
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).operationalDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(LocalDate.of(2026, 5, 31).plusMonths(9)).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void manualSourceSkipsCandidateQuery() {
        stubLegacy(List.of(), 0);

        service.page(c, HerdAgendaSource.MANUAL, null, null, null, null, 0, 20);

        verify(herd, never()).brucellosisPendingCandidates(any(), any(), any(), any());
        verify(agenda).page(eq(tenant), eq(farm), eq(today), eq(90), eq(14),
                eq(HerdAgendaSource.MANUAL), isNull(), isNull(), isNull(), isNull(), eq(20), eq(0L));
    }

    @Test
    void nonVaccinationKindSkipsCandidateQuery() {
        stubLegacy(List.of(), 0);

        service.page(c, null, HerdPlannerType.WEIGHING, null, null, null, 0, 20);

        verify(herd, never()).brucellosisPendingCandidates(any(), any(), any(), any());
    }

    @Test
    void vaccinationKindIsEligible() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 1, 15)));

        ReadHerdAgenda.Result result = service.page(c, null, HerdPlannerType.VACCINATION, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        verify(herd, times(1)).brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), isNull());
    }

    @Test
    void animalFilterReachesCandidateQuery() {
        HerdManagementRepository.BrucellosisPendingCandidate only =
                candidate(LocalDate.of(2026, 1, 15));
        UUID wanted = only.animalId();
        stubLegacy(List.of(), 0);
        when(herd.brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), eq(wanted)))
                .thenReturn(List.of(only));

        ReadHerdAgenda.Result result = service.page(c, null, null, wanted,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).animalId()).isEqualTo(wanted);
    }

    @Test
    void onlyLegacyKeepsPageAndTotal() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stubLegacy(List.of(
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", LocalDate.of(2026, 10, 1),
                        first + ":VACCINATION_DUE", first),
                legacyRow(HerdAgendaSource.MANUAL, "VACCINATION", LocalDate.of(2026, 10, 15),
                        UUID.randomUUID().toString(), second)), 2);
        stubCandidates();

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).extracting(ReadHerdAgenda.Item::operationalDate)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15));
    }

    @Test
    void onlyBrucellosisShowsDerivedItems() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 1, 15)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items().get(0).operationalDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void mixedSourcesMergeByGlobalOrder() {
        stubLegacy(List.of(
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", LocalDate.of(2026, 10, 1),
                        UUID.randomUUID() + ":VACCINATION_DUE", UUID.randomUUID()),
                legacyRow(HerdAgendaSource.DERIVED, "DEWORMING", LocalDate.of(2026, 12, 1),
                        UUID.randomUUID() + ":DEWORMING_DUE", UUID.randomUUID())), 2);
        stubCandidates(candidate(LocalDate.of(2026, 2, 1)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.items()).extracting(ReadHerdAgenda.Item::operationalDate)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1));
    }

    @Test
    void tiesFollowSourceKindStableIdWithoutOrdinal() {
        UUID animal = UUID.randomUUID();
        stubLegacy(List.of(
                legacyRow(HerdAgendaSource.MANUAL, "VACCINATION", LocalDate.of(2026, 10, 1),
                        UUID.randomUUID().toString(), animal),
                legacyRow(HerdAgendaSource.DERIVED, "WEIGHING", LocalDate.of(2026, 10, 1),
                        animal + ":WEIGHING_DUE", animal)), 2);
        stubCandidates(candidate(LocalDate.of(2026, 1, 1)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.items()).extracting(ReadHerdAgenda.Item::kind)
                .containsExactly("VACCINATION", "WEIGHING", "VACCINATION");
        assertThat(result.items()).extracting(ReadHerdAgenda.Item::source)
                .containsExactly(HerdAgendaSource.DERIVED, HerdAgendaSource.DERIVED, HerdAgendaSource.MANUAL);
    }

    @Test
    void middlePageFetchesLegacyPrefixFromZero() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 15);
        LocalDate d3 = LocalDate.of(2026, 12, 1);
        LocalDate d4 = LocalDate.of(2026, 12, 15);
        stubLegacy(List.of(
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", d1, UUID.randomUUID() + ":VACCINATION_DUE",
                        UUID.randomUUID()),
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", d2, UUID.randomUUID() + ":VACCINATION_DUE",
                        UUID.randomUUID()),
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", d3, UUID.randomUUID() + ":VACCINATION_DUE",
                        UUID.randomUUID()),
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", d4, UUID.randomUUID() + ":VACCINATION_DUE",
                        UUID.randomUUID())), 4);
        stubCandidates(candidate(LocalDate.of(2026, 2, 1)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 1, 2);

        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.items()).extracting(ReadHerdAgenda.Item::operationalDate)
                .containsExactly(LocalDate.of(2026, 11, 1), d3);
        verify(agenda).page(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(4), eq(0L));
        verify(agenda, times(1)).count(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull(),
                isNull(), isNull(), isNull());
        verify(herd, times(1)).brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), isNull());
    }

    @Test
    void lastPageSlicesPartialTail() {
        stubLegacy(List.of(
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", LocalDate.of(2026, 10, 1),
                        UUID.randomUUID() + ":VACCINATION_DUE", UUID.randomUUID()),
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", LocalDate.of(2026, 10, 15),
                        UUID.randomUUID() + ":VACCINATION_DUE", UUID.randomUUID())), 2);
        stubCandidates(candidate(LocalDate.of(2026, 2, 1)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 1, 2);

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.items()).extracting(ReadHerdAgenda.Item::operationalDate)
                .containsExactly(LocalDate.of(2026, 11, 1));
    }

    @Test
    void pastEndReturnsEmptyWithoutLegacyPrefixFetch() {
        stubLegacy(List.of(
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", LocalDate.of(2026, 10, 1),
                        UUID.randomUUID() + ":VACCINATION_DUE", UUID.randomUUID())), 1);
        stubCandidates(candidate(LocalDate.of(2026, 4, 22)));

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 5, 2);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).isEmpty();
        verify(agenda, never()).page(any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(),
                anyInt(), anyLong());
        verify(agenda, times(1)).count(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull(),
                isNull(), isNull(), isNull());
    }

    @Test
    void rejectsLegacyPrefixBeyondIntegerLimitWithoutFetching() {
        stubLegacy(List.of(), 2_147_483_700L);
        stubCandidates();

        assertThatThrownBy(() -> service.page(c, null, null, null, null, null, 21_474_836, 100))
                .isInstanceOf(HerdPlannerExceptions.QueryInvalid.class);
        verify(agenda, never()).page(any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(),
                anyInt(), anyLong());
    }

    @Test
    void legacyVaccinationAndBrucellosisCoexistForSameAnimal() {
        UUID animal = UUID.randomUUID();
        stubLegacy(List.of(
                legacyRow(HerdAgendaSource.DERIVED, "VACCINATION", LocalDate.of(2026, 11, 1),
                        animal + ":VACCINATION_DUE", animal)), 1);
        HerdManagementRepository.BrucellosisPendingCandidate only =
                candidate(LocalDate.of(2026, 2, 1));
        UUID brucAnimal = only.animalId();
        stubCandidates(only);

        ReadHerdAgenda.Result result = service.page(c, null, null, null, null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).extracting(ReadHerdAgenda.Item::stableId)
                .containsExactlyInAnyOrder(brucAnimal + ":BRUCELLOSIS_DUE", animal + ":VACCINATION_DUE");
    }
}
