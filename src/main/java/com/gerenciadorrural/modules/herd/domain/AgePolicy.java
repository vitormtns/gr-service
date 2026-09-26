package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class AgePolicy {

    private AgePolicy() {
    }

    public static int completedMonths(LocalDate birthDate, LocalDate referenceDate) {
        requireValidRange(birthDate, referenceDate);
        int months = (referenceDate.getYear() - birthDate.getYear()) * 12
                + (referenceDate.getMonthValue() - birthDate.getMonthValue());
        if (referenceDate.getDayOfMonth() < birthDate.getDayOfMonth()) {
            months--;
        }
        return months;
    }

    public static AgeBand classify(LocalDate birthDate, LocalDate referenceDate) {
        int months = completedMonths(birthDate, referenceDate);
        if (months <= 2) {
            return AgeBand.MONTHS_0_2;
        }
        if (months <= 8) {
            return AgeBand.MONTHS_3_8;
        }
        if (months <= 12) {
            return AgeBand.MONTHS_9_12;
        }
        if (months <= 24) {
            return AgeBand.MONTHS_13_24;
        }
        if (months <= 36) {
            return AgeBand.MONTHS_25_36;
        }
        return AgeBand.MONTHS_37_PLUS;
    }

    public static Optional<AgeBand> nextBand(LocalDate birthDate, LocalDate referenceDate) {
        return switch (classify(birthDate, referenceDate)) {
            case MONTHS_0_2 -> Optional.of(AgeBand.MONTHS_3_8);
            case MONTHS_3_8 -> Optional.of(AgeBand.MONTHS_9_12);
            case MONTHS_9_12 -> Optional.of(AgeBand.MONTHS_13_24);
            case MONTHS_13_24 -> Optional.of(AgeBand.MONTHS_25_36);
            case MONTHS_25_36 -> Optional.of(AgeBand.MONTHS_37_PLUS);
            case MONTHS_37_PLUS -> Optional.empty();
        };
    }

    public static Optional<LocalDate> nextTransitionDate(LocalDate birthDate, LocalDate referenceDate) {
        return switch (classify(birthDate, referenceDate)) {
            case MONTHS_0_2 -> Optional.of(transitionDate(birthDate, 3));
            case MONTHS_3_8 -> Optional.of(transitionDate(birthDate, 9));
            case MONTHS_9_12 -> Optional.of(transitionDate(birthDate, 13));
            case MONTHS_13_24 -> Optional.of(transitionDate(birthDate, 25));
            case MONTHS_25_36 -> Optional.of(transitionDate(birthDate, 37));
            case MONTHS_37_PLUS -> Optional.empty();
        };
    }

    public static LocalDate reachesMonthsOn(LocalDate birthDate, int months) {
        Objects.requireNonNull(birthDate, "A data de nascimento é obrigatória");
        if (months < 0) {
            throw new IllegalArgumentException("O número de meses não pode ser negativo");
        }
        if (months == 0) {
            return birthDate;
        }
        return transitionDate(birthDate, months);
    }

    private static LocalDate transitionDate(LocalDate birthDate, int boundary) {
        LocalDate candidate = birthDate.plusMonths(boundary);
        if (completedMonths(birthDate, candidate) >= boundary) {
            return candidate;
        }
        return candidate.plusDays(1);
    }

    private static void requireValidRange(LocalDate birthDate, LocalDate referenceDate) {
        Objects.requireNonNull(birthDate, "A data de nascimento é obrigatória");
        Objects.requireNonNull(referenceDate, "A data de referência é obrigatória");
        if (birthDate.isAfter(referenceDate)) {
            throw new IllegalArgumentException("A data de nascimento não pode ser posterior à data de referência");
        }
    }
}
