package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReadHerdPendingWorkBrucellosisTest {

    TenantTransactionExecutor tx = mock();
    HerdManagementRepository repo = mock();

    TenantId tenant = new TenantId(UUID.randomUUID());
    UUID user = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    TenantContext c = new TenantContext(tenant, user, farm, UUID.randomUUID(), "OPERATOR", "ALL_FARMS");

    LocalDate today = LocalDate.of(2026, 9, 22);
    ReadHerdPendingWork service;

    @BeforeEach
    void setup() {
        doAnswer(x -> ((TenantTransactionalOperation<?>) x.getArgument(1)).execute())
                .when(tx).execute(any(), org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
        service = new ReadHerdPendingWork(tx, repo, Clock.fixed(
                today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC), 90, 14);
    }

    private HerdManagementRepository.Treatment dose(LocalDate occurredOn) {
        return new HerdManagementRepository.Treatment(UUID.randomUUID(), UUID.randomUUID(),
                HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                occurredOn, null, null, null, null, user, Instant.now());
    }

    private HerdManagementRepository.BrucellosisPendingCandidate candidate(
            LocalDate birth, HerdManagementRepository.Treatment... doses) {
        return candidateWithId(UUID.randomUUID(), birth, doses);
    }

    private HerdManagementRepository.BrucellosisPendingCandidate candidateWithId(
            UUID id, LocalDate birth, HerdManagementRepository.Treatment... doses) {
        return new HerdManagementRepository.BrucellosisPendingCandidate(id, "B-" + id.toString().substring(0, 4),
                null, farm, HerdAnimalSex.FEMALE, birth, List.of(doses));
    }

    private HerdManagementRepository.Pending legacy(
            PendingWorkType type, UUID animalId, LocalDate date, UUID pregnancyId) {
        return new HerdManagementRepository.Pending(type, animalId, "L-" + animalId.toString().substring(0, 4),
                null, farm, date, pregnancyId, null, null, null);
    }

    private void stubLegacy(List<HerdManagementRepository.Pending> rows, long total) {
        when(repo.pending(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull(), anyInt(), anyLong()))
                .thenReturn(rows);
        when(repo.pendingCount(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull()))
                .thenReturn(total);
    }

    private void stubCandidates(HerdManagementRepository.BrucellosisPendingCandidate... candidates) {
        when(repo.brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), any()))
                .thenReturn(List.of(candidates));
    }

    @Test
    void dueForFiveMonthOldFemaleWithoutDose() {
        stubCandidates(candidate(LocalDate.of(2026, 4, 22)));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).type()).isEqualTo(PendingWorkType.BRUCELLOSIS_DUE);
        assertThat(page.items().get(0).dueOn()).isEqualTo(LocalDate.of(2027, 1, 22));
        assertThat(page.items().get(0).daysUntil()).isEqualTo(122L);
        assertThat(page.items().get(0).daysOverdue()).isNull();
        assertThat(page.items().get(0).expectedOn()).isNull();
        assertThat(page.items().get(0).treatmentType()).isEqualTo(HealthTreatmentType.VACCINATION);
        verify(repo, never()).pending(any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(repo, never()).pendingCount(any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void dueForEightMonthOldFemaleWithoutDose() {
        stubCandidates(candidate(LocalDate.of(2026, 1, 22)));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).dueOn()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void noDueWhenPrimaryRecorded() {
        stubCandidates(candidate(LocalDate.of(2026, 4, 22), dose(LocalDate.of(2026, 7, 22))));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    void noDueForTwoMonthOldFemale() {
        stubCandidates(candidate(LocalDate.of(2026, 7, 22)));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isZero();
    }

    @Test
    void noDueForPostWindowRecord() {
        stubCandidates(candidate(LocalDate.of(2025, 9, 22), dose(LocalDate.of(2026, 7, 22))));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isZero();
    }

    @Test
    void dueKeepsPreWindowDoseAsLastPerformed() {
        stubCandidates(candidate(LocalDate.of(2026, 4, 22), dose(LocalDate.of(2026, 5, 22))));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).lastPerformedOn()).isEqualTo(LocalDate.of(2026, 5, 22));
    }

    @Test
    void missedForNineMonthOldFemaleWithoutDose() {
        stubCandidates(candidate(LocalDate.of(2025, 12, 22)));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_WINDOW_MISSED, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).type()).isEqualTo(PendingWorkType.BRUCELLOSIS_WINDOW_MISSED);
        assertThat(page.items().get(0).dueOn()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(page.items().get(0).daysOverdue()).isZero();
        assertThat(page.items().get(0).daysUntil()).isNull();
    }

    @Test
    void missedForTwelveMonthOldFemaleWithoutDose() {
        stubCandidates(candidate(LocalDate.of(2025, 9, 22)));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_WINDOW_MISSED, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).dueOn()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(page.items().get(0).daysOverdue()).isEqualTo(92L);
    }

    @Test
    void noMissedWhenPrimaryRecorded() {
        stubCandidates(candidate(LocalDate.of(2025, 9, 22), dose(LocalDate.of(2026, 1, 22))));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_WINDOW_MISSED, null, 0, 20);

        assertThat(page.totalElements()).isZero();
    }

    @Test
    void noMissedForPostWindowRecord() {
        stubCandidates(candidate(LocalDate.of(2025, 9, 22), dose(LocalDate.of(2026, 7, 22))));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_WINDOW_MISSED, null, 0, 20);

        assertThat(page.totalElements()).isZero();
    }

    @Test
    void dueOnUsesReachesMonthsOnInsteadOfPlusMonthsForClampedBirth() {
        LocalDate birth = LocalDate.of(2026, 5, 31);
        stubCandidates(candidate(birth));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(birth.plusMonths(9)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(page.items().get(0).dueOn()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(page.items().get(0).daysUntil()).isEqualTo(160L);
    }

    @Test
    void futureDoseDoesNotChangeSnapshotState() {
        stubCandidates(candidate(LocalDate.of(2026, 4, 22), dose(LocalDate.of(2026, 10, 22))));

        ReadHerdPendingWork.Page page = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).lastPerformedOn()).isNull();
    }

    @Test
    void paginatesFiveCandidatesWithSizeTwo() {
        stubCandidates(
                candidate(LocalDate.of(2026, 1, 25)),
                candidate(LocalDate.of(2026, 2, 25)),
                candidate(LocalDate.of(2026, 3, 25)),
                candidate(LocalDate.of(2026, 4, 25)),
                candidate(LocalDate.of(2026, 5, 25)));

        ReadHerdPendingWork.Page first = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 2);
        ReadHerdPendingWork.Page second = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 1, 2);
        ReadHerdPendingWork.Page third = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 2, 2);

        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(second.totalElements()).isEqualTo(5);
        assertThat(third.totalElements()).isEqualTo(5);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(third.items()).hasSize(1);
        List<LocalDate> dueDates = new ArrayList<>();
        dueDates.addAll(first.items().stream().map(ReadHerdPendingWork.Item::dueOn).toList());
        dueDates.addAll(second.items().stream().map(ReadHerdPendingWork.Item::dueOn).toList());
        dueDates.addAll(third.items().stream().map(ReadHerdPendingWork.Item::dueOn).toList());
        assertThat(dueDates).containsExactly(
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 25),
                LocalDate.of(2027, 1, 25), LocalDate.of(2027, 2, 25));
    }

    @Test
    void explicitBrucellosisPagesUsePostgresUuidOrderForDateTies() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        stubCandidates(candidateWithId(high, LocalDate.of(2026, 4, 22)),
                candidateWithId(low, LocalDate.of(2026, 4, 22)));

        ReadHerdPendingWork.Page first = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 0, 1);
        ReadHerdPendingWork.Page second = service.page(c, PendingWorkType.BRUCELLOSIS_DUE, null, 1, 1);

        assertThat(first.items()).extracting(ReadHerdPendingWork.Item::animalId).containsExactly(low);
        assertThat(second.items()).extracting(ReadHerdPendingWork.Item::animalId).containsExactly(high);
    }

    @Test
    void respectsAnimalIdFilter() {
        HerdManagementRepository.BrucellosisPendingCandidate only =
                candidate(LocalDate.of(2026, 4, 22));
        when(repo.brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), eq(only.animalId())))
                .thenReturn(List.of(only));

        ReadHerdPendingWork.Page page =
                service.page(c, PendingWorkType.BRUCELLOSIS_DUE, only.animalId(), 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().get(0).animalId()).isEqualTo(only.animalId());
        verify(repo).brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), eq(only.animalId()));
    }

    @Test
    void nullTypeUsesMergeWithCorrectCallCounts() {
        when(repo.pending(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull(), eq(3), eq(0L)))
                .thenReturn(List.of(
                        legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 10, 1), null),
                        legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 10, 15), null),
                        legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 12, 1), null)));
        when(repo.pendingCount(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull()))
                .thenReturn(3L);
        stubCandidates(candidate(LocalDate.of(2026, 4, 22)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(4);
        verify(repo, times(1)).pending(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull(), eq(3), eq(0L));
        verify(repo, times(1)).pendingCount(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull());
        verify(repo, times(1)).brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), isNull());
    }

    @Test
    void legacyTypeDoesNotCallCandidateQuery() {
        when(repo.pending(eq(tenant), eq(farm), eq(today), eq(90), eq(14),
                eq(PendingWorkType.WEIGHING_DUE), isNull(), eq(20), eq(0L))).thenReturn(List.of());
        when(repo.pendingCount(eq(tenant), eq(farm), eq(today), eq(90), eq(14),
                eq(PendingWorkType.WEIGHING_DUE), isNull())).thenReturn(0L);

        service.page(c, PendingWorkType.WEIGHING_DUE, null, 0, 20);

        verify(repo, never()).brucellosisPendingCandidates(any(), any(), any(), any());
    }

    @Test
    void nullTypeWithOnlyLegacyKeepsOrderPageAndTotal() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stubLegacy(List.of(
                legacy(PendingWorkType.VACCINATION_DUE, first, LocalDate.of(2026, 10, 1), null),
                legacy(PendingWorkType.DEWORMING_DUE, second, LocalDate.of(2026, 12, 1), null)), 2);
        stubCandidates();

        ReadHerdPendingWork.Page page = service.page(c, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::type)
                .containsExactly(PendingWorkType.VACCINATION_DUE, PendingWorkType.DEWORMING_DUE);
        verify(repo).brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), isNull());
    }

    @Test
    void nullTypeWithOnlyBrucellosisShowsDerivedItems() {
        stubLegacy(List.of(), 0);
        stubCandidates(candidate(LocalDate.of(2026, 4, 22)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(PendingWorkType.BRUCELLOSIS_DUE);
            assertThat(item.dueOn()).isEqualTo(LocalDate.of(2027, 1, 22));
        });
    }

    @Test
    void nullTypeMergesInterleavedSourcesByGlobalDate() {
        stubLegacy(List.of(
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 10, 1), null),
                legacy(PendingWorkType.DEWORMING_DUE, UUID.randomUUID(), LocalDate.of(2026, 12, 1), null)), 2);
        stubCandidates(candidate(LocalDate.of(2026, 2, 1)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::dueOn)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1));
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::type)
                .containsExactly(PendingWorkType.VACCINATION_DUE, PendingWorkType.BRUCELLOSIS_DUE,
                        PendingWorkType.DEWORMING_DUE);
    }

    @Test
    void nullTypeOrdersByTypeNameInsteadOfEnumOrdinal() {
        UUID animal = UUID.randomUUID();
        stubLegacy(List.of(
                legacy(PendingWorkType.CALVING_UPCOMING, animal, LocalDate.of(2026, 10, 1), UUID.randomUUID())), 1);
        stubCandidates(candidate(LocalDate.of(2026, 1, 1)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::type)
                .containsExactly(PendingWorkType.BRUCELLOSIS_DUE, PendingWorkType.CALVING_UPCOMING);
    }

    @Test
    void nullTypeBreaksAnimalTiesByUnsignedUuidOrder() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        stubLegacy(List.of(
                legacy(PendingWorkType.WEIGHING_DUE, high, LocalDate.of(2026, 10, 1), null),
                legacy(PendingWorkType.WEIGHING_DUE, low, LocalDate.of(2026, 10, 1), null)), 2);
        stubCandidates();

        ReadHerdPendingWork.Page page = service.page(c, null, null, 0, 20);

        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::animalId)
                .containsExactly(low, high);
    }

    @Test
    void nullTypeMiddlePageFetchesLegacyPrefixFromZero() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 15);
        LocalDate d3 = LocalDate.of(2026, 12, 1);
        LocalDate d4 = LocalDate.of(2026, 12, 15);
        stubLegacy(List.of(
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), d1, null),
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), d2, null),
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), d3, null),
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), d4, null)), 4);
        stubCandidates(candidate(LocalDate.of(2026, 2, 1)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 1, 2);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::dueOn)
                .containsExactly(LocalDate.of(2026, 11, 1), d3);
        verify(repo).pending(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull(), eq(4), eq(0L));
        verify(repo, times(1)).pendingCount(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull());
        verify(repo, times(1)).brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), isNull());
    }

    @Test
    void nullTypeLastPageSlicesPartialTail() {
        stubLegacy(List.of(
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 10, 1), null),
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 10, 15), null),
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 12, 1), null),
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 12, 15), null)), 4);
        stubCandidates(candidate(LocalDate.of(2026, 2, 1)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 2, 2);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::dueOn)
                .containsExactly(LocalDate.of(2026, 12, 15));
    }

    @Test
    void nullTypePastEndReturnsEmptyWithoutLegacyPrefixFetch() {
        stubLegacy(List.of(
                legacy(PendingWorkType.VACCINATION_DUE, UUID.randomUUID(), LocalDate.of(2026, 10, 1), null)), 1);
        stubCandidates(candidate(LocalDate.of(2026, 4, 22)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 5, 2);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).isEmpty();
        verify(repo, never()).pending(any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(repo, times(1)).pendingCount(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), isNull());
    }

    @Test
    void nullTypeRejectsPrefixBeyondIntegerLimitWithoutFetching() {
        stubLegacy(List.of(), Long.MAX_VALUE);
        stubCandidates();

        assertThatThrownBy(() -> service.page(c, null, null, Integer.MAX_VALUE, 1))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);
        verify(repo, never()).pending(any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt(), anyLong());
    }

    @Test
    void nullTypeRespectsAnimalFilterOnBothSources() {
        UUID wanted = UUID.randomUUID();
        HerdManagementRepository.BrucellosisPendingCandidate only = candidateWithId(wanted, LocalDate.of(2026, 4, 22));
        when(repo.pending(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), eq(wanted), eq(1), eq(0L)))
                .thenReturn(List.of(legacy(PendingWorkType.VACCINATION_DUE, wanted, LocalDate.of(2026, 10, 1), null)));
        when(repo.pendingCount(eq(tenant), eq(farm), eq(today), eq(90), eq(14), isNull(), eq(wanted)))
                .thenReturn(1L);
        when(repo.brucellosisPendingCandidates(eq(tenant), eq(farm), eq(today), eq(wanted)))
                .thenReturn(List.of(only));

        ReadHerdPendingWork.Page page = service.page(c, null, wanted, 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::animalId)
                .containsExactly(wanted, wanted);
    }

    @Test
    void nullTypeKeepsGenericVaccinationAndBrucellosisForSameAnimal() {
        UUID animal = UUID.randomUUID();
        stubLegacy(List.of(
                legacy(PendingWorkType.VACCINATION_DUE, animal, LocalDate.of(2026, 10, 1), null)), 1);
        stubCandidates(candidateWithId(animal, LocalDate.of(2026, 4, 22)));

        ReadHerdPendingWork.Page page = service.page(c, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(ReadHerdPendingWork.Item::type)
                .containsExactly(PendingWorkType.VACCINATION_DUE, PendingWorkType.BRUCELLOSIS_DUE);
    }
}
