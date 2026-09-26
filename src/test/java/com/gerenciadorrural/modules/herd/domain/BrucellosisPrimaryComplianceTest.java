package com.gerenciadorrural.modules.herd.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrucellosisPrimaryComplianceTest {

    private static final LocalDate BIRTH = LocalDate.of(2026, 1, 15);

    private static HerdManagementRepository.Treatment treatment(
            HealthTreatmentType type, HealthProcedureCode procedureCode, LocalDate occurredOn, String product) {
        return new HerdManagementRepository.Treatment(
                UUID.randomUUID(), UUID.randomUUID(), type, procedureCode, occurredOn,
                product, null, null, null, null, Instant.EPOCH);
    }

    private static HerdManagementRepository.Treatment brucellosisDose(LocalDate occurredOn) {
        return treatment(HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS, occurredOn, null);
    }

    @Test
    void shouldStayBeforeWindowBelowThreeMonths() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 3, 15), List.of()))
                .isEqualTo(BrucellosisPrimaryState.BEFORE_WINDOW);
    }

    @Test
    void shouldStayDueInsideWindowWithoutDose() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 4, 15), List.of()))
                .isEqualTo(BrucellosisPrimaryState.DUE_IN_WINDOW);
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 9, 15), List.of()))
                .isEqualTo(BrucellosisPrimaryState.DUE_IN_WINDOW);
    }

    @Test
    void shouldMissWindowWithoutDose() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 10, 15), List.of()))
                .isEqualTo(BrucellosisPrimaryState.WINDOW_MISSED);
    }

    @Test
    void shouldRecordPrimaryVaccinationInsideWindow() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 6, 15),
                List.of(brucellosisDose(LocalDate.of(2026, 5, 15)))))
                .isEqualTo(BrucellosisPrimaryState.PRIMARY_VACCINATION_RECORDED);
    }

    @Test
    void shouldKeepPrimaryVaccinationAfterWindow() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(brucellosisDose(LocalDate.of(2026, 6, 15)))))
                .isEqualTo(BrucellosisPrimaryState.PRIMARY_VACCINATION_RECORDED);
    }

    @Test
    void shouldMissWindowWhenDoseOnlyBeforeWindow() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(brucellosisDose(LocalDate.of(2026, 3, 15)))))
                .isEqualTo(BrucellosisPrimaryState.WINDOW_MISSED);
    }

    @Test
    void shouldStayDueWhenOnlyPreWindowDoseAndStillInsideWindow() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 6, 15),
                List.of(brucellosisDose(LocalDate.of(2026, 3, 15)))))
                .isEqualTo(BrucellosisPrimaryState.DUE_IN_WINDOW);
    }

    @Test
    void shouldFlagPostWindowRecordAsUnverified() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(brucellosisDose(LocalDate.of(2026, 11, 15)))))
                .isEqualTo(BrucellosisPrimaryState.POST_WINDOW_RECORD_UNVERIFIED);
    }

    @Test
    void shouldFlagPreWindowPlusPostWindowDosesAsUnverified() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(
                        brucellosisDose(LocalDate.of(2026, 3, 15)),
                        brucellosisDose(LocalDate.of(2026, 11, 15)))))
                .isEqualTo(BrucellosisPrimaryState.POST_WINDOW_RECORD_UNVERIFIED);
    }

    @Test
    void shouldPreferPrimaryDoseOverPostWindowDose() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(
                        brucellosisDose(LocalDate.of(2026, 6, 15)),
                        brucellosisDose(LocalDate.of(2026, 11, 15)))))
                .isEqualTo(BrucellosisPrimaryState.PRIMARY_VACCINATION_RECORDED);
    }

    @Test
    void shouldIgnorePostWindowProductText() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(treatment(
                        HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                        LocalDate.of(2026, 11, 15), "RB51"))))
                .isEqualTo(BrucellosisPrimaryState.POST_WINDOW_RECORD_UNVERIFIED);
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(treatment(
                        HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                        LocalDate.of(2026, 11, 15), "B19"))))
                .isEqualTo(BrucellosisPrimaryState.POST_WINDOW_RECORD_UNVERIFIED);
    }

    @Test
    void shouldEvaluateHistoricalSnapshotsWithoutFutureLeak() {
        List<HerdManagementRepository.Treatment> history =
                List.of(brucellosisDose(LocalDate.of(2026, 6, 15)));

        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 5, 15), history))
                .isEqualTo(BrucellosisPrimaryState.DUE_IN_WINDOW);
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 7, 15), history))
                .isEqualTo(BrucellosisPrimaryState.PRIMARY_VACCINATION_RECORDED);
    }

    @Test
    void shouldIgnoreNonBrucellosisTreatments() {
        LocalDate reference = LocalDate.of(2026, 11, 15);
        List<HerdManagementRepository.Treatment> history = List.of(
                treatment(HealthTreatmentType.VACCINATION, null, LocalDate.of(2026, 6, 15), null),
                treatment(HealthTreatmentType.VACCINATION, null, LocalDate.of(2026, 6, 15), "Brucelose"),
                treatment(HealthTreatmentType.DEWORMING, null, LocalDate.of(2026, 6, 15), null),
                treatment(HealthTreatmentType.DEWORMING, HealthProcedureCode.BRUCELLOSIS,
                        LocalDate.of(2026, 6, 15), null));

        assertThat(BrucellosisPrimaryCompliance.evaluate(HerdAnimalSex.FEMALE, BIRTH, reference, history))
                .isEqualTo(BrucellosisPrimaryState.WINDOW_MISSED);
        assertThat(BrucellosisPrimaryCompliance.evaluate(HerdAnimalSex.MALE, BIRTH, reference, history))
                .isEqualTo(BrucellosisPrimaryState.NOT_APPLICABLE);
    }

    @Test
    void shouldFlagProhibitedVaccinationForMale() {
        LocalDate reference = LocalDate.of(2026, 6, 15);

        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.MALE, BIRTH, reference, List.of()))
                .isEqualTo(BrucellosisPrimaryState.NOT_APPLICABLE);
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.MALE, BIRTH, reference,
                List.of(treatment(HealthTreatmentType.VACCINATION, null, LocalDate.of(2026, 5, 15), null))))
                .isEqualTo(BrucellosisPrimaryState.NOT_APPLICABLE);
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.MALE, BIRTH, reference,
                List.of(brucellosisDose(LocalDate.of(2026, 5, 15)))))
                .isEqualTo(BrucellosisPrimaryState.PROHIBITED_VACCINATION_RECORDED);
    }

    @Test
    void shouldIgnoreFutureMaleRecordInPastSnapshot() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.MALE, BIRTH, LocalDate.of(2026, 6, 15),
                List.of(brucellosisDose(LocalDate.of(2026, 8, 15)))))
                .isEqualTo(BrucellosisPrimaryState.NOT_APPLICABLE);
    }

    @Test
    void shouldIgnorePreBirthMaleRecord() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.MALE, BIRTH, LocalDate.of(2026, 6, 15),
                List.of(brucellosisDose(LocalDate.of(2025, 12, 15)))))
                .isEqualTo(BrucellosisPrimaryState.NOT_APPLICABLE);
    }

    @Test
    void shouldFollowAgePolicyCalendarSemanticsForEndOfMonthBirthDate() {
        LocalDate birth = LocalDate.of(2026, 1, 31);

        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, birth, LocalDate.of(2026, 4, 30), List.of()))
                .isEqualTo(BrucellosisPrimaryState.BEFORE_WINDOW);
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, birth, LocalDate.of(2026, 6, 15),
                List.of(brucellosisDose(LocalDate.of(2026, 5, 1)))))
                .isEqualTo(BrucellosisPrimaryState.PRIMARY_VACCINATION_RECORDED);
    }

    @Test
    void shouldIgnorePreBirthTreatmentWithoutFailing() {
        assertThat(BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 11, 15),
                List.of(brucellosisDose(LocalDate.of(2025, 12, 15)))))
                .isEqualTo(BrucellosisPrimaryState.WINDOW_MISSED);
    }

    @Test
    void shouldRejectNullInputs() {
        LocalDate reference = LocalDate.of(2026, 6, 15);

        assertThatThrownBy(() -> BrucellosisPrimaryCompliance.evaluate(null, BIRTH, reference, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BrucellosisPrimaryCompliance.evaluate(HerdAnimalSex.FEMALE, null, reference, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BrucellosisPrimaryCompliance.evaluate(HerdAnimalSex.FEMALE, BIRTH, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BrucellosisPrimaryCompliance.evaluate(HerdAnimalSex.FEMALE, BIRTH, reference, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, BIRTH, reference, Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectBirthDateAfterReferenceDate() {
        LocalDate birth = LocalDate.of(2026, 4, 15);
        LocalDate reference = LocalDate.of(2026, 4, 14);

        assertThatThrownBy(() -> BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.FEMALE, birth, reference, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrucellosisPrimaryCompliance.evaluate(
                HerdAnimalSex.MALE, birth, reference, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
