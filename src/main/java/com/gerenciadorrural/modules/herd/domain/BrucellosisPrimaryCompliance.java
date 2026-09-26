package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class BrucellosisPrimaryCompliance {

    private BrucellosisPrimaryCompliance() {
    }

    public static BrucellosisPrimaryState evaluate(
            HerdAnimalSex sex,
            LocalDate birthDate,
            LocalDate referenceDate,
            List<HerdManagementRepository.Treatment> treatments) {
        Objects.requireNonNull(sex, "O sexo é obrigatório");
        Objects.requireNonNull(birthDate, "A data de nascimento é obrigatória");
        Objects.requireNonNull(referenceDate, "A data de referência é obrigatória");
        Objects.requireNonNull(treatments, "A lista de tratamentos é obrigatória");
        if (birthDate.isAfter(referenceDate)) {
            throw new IllegalArgumentException("A data de nascimento não pode ser posterior à data de referência");
        }
        for (HerdManagementRepository.Treatment treatment : treatments) {
            Objects.requireNonNull(treatment, "A lista de tratamentos não pode conter elementos nulos");
        }
        if (sex != HerdAnimalSex.FEMALE) {
            return hasStructuredRecordUntil(treatments, birthDate, referenceDate)
                    ? BrucellosisPrimaryState.PROHIBITED_VACCINATION_RECORDED
                    : BrucellosisPrimaryState.NOT_APPLICABLE;
        }
        if (hasPrimaryQualifyingDose(treatments, birthDate, referenceDate)) {
            return BrucellosisPrimaryState.PRIMARY_VACCINATION_RECORDED;
        }
        return switch (BrucellosisPolicy.window(sex, birthDate, referenceDate)) {
            case BEFORE_MANDATORY_WINDOW -> BrucellosisPrimaryState.BEFORE_WINDOW;
            case MANDATORY_WINDOW -> BrucellosisPrimaryState.DUE_IN_WINDOW;
            case AFTER_MANDATORY_WINDOW -> hasPostWindowRecord(treatments, birthDate, referenceDate)
                    ? BrucellosisPrimaryState.POST_WINDOW_RECORD_UNVERIFIED
                    : BrucellosisPrimaryState.WINDOW_MISSED;
            case NOT_APPLICABLE -> throw new IllegalStateException("Janela inesperada para fêmea");
        };
    }

    private static boolean isStructuredBrucellosisDose(HerdManagementRepository.Treatment treatment) {
        return treatment.type() == HealthTreatmentType.VACCINATION
                && treatment.procedureCode() == HealthProcedureCode.BRUCELLOSIS;
    }

    private static boolean occurredUntil(HerdManagementRepository.Treatment treatment, LocalDate referenceDate) {
        return treatment.occurredOn() != null && !treatment.occurredOn().isAfter(referenceDate);
    }

    private static boolean hasStructuredRecordUntil(
            List<HerdManagementRepository.Treatment> treatments, LocalDate birthDate, LocalDate referenceDate) {
        for (HerdManagementRepository.Treatment treatment : treatments) {
            if (isStructuredBrucellosisDose(treatment) && occurredUntil(treatment, referenceDate)
                    && !treatment.occurredOn().isBefore(birthDate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPrimaryQualifyingDose(
            List<HerdManagementRepository.Treatment> treatments, LocalDate birthDate, LocalDate referenceDate) {
        for (HerdManagementRepository.Treatment treatment : treatments) {
            if (!isStructuredBrucellosisDose(treatment) || !occurredUntil(treatment, referenceDate)) {
                continue;
            }
            if (treatment.occurredOn().isBefore(birthDate)) {
                continue;
            }
            int months = AgePolicy.completedMonths(birthDate, treatment.occurredOn());
            if (months >= 3 && months <= 8) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPostWindowRecord(
            List<HerdManagementRepository.Treatment> treatments, LocalDate birthDate, LocalDate referenceDate) {
        for (HerdManagementRepository.Treatment treatment : treatments) {
            if (!isStructuredBrucellosisDose(treatment) || !occurredUntil(treatment, referenceDate)) {
                continue;
            }
            if (treatment.occurredOn().isBefore(birthDate)) {
                continue;
            }
            if (AgePolicy.completedMonths(birthDate, treatment.occurredOn()) >= 9) {
                return true;
            }
        }
        return false;
    }
}
