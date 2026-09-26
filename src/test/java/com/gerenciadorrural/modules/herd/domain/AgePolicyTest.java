package com.gerenciadorrural.modules.herd.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgePolicyTest {

    private static final LocalDate BIRTH = LocalDate.of(2026, 1, 15);

    @Test
    void shouldCountZeroMonthsOnBirthDate() {
        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2026, 1, 15))).isZero();
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 1, 15))).isEqualTo(AgeBand.MONTHS_0_2);
    }

    @Test
    void shouldClassifyTwoMonthsBeforeThreeMonthTransition() {
        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2026, 3, 15))).isEqualTo(2);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 3, 15))).isEqualTo(AgeBand.MONTHS_0_2);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2026, 4, 14))).isEqualTo(2);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 4, 14))).isEqualTo(AgeBand.MONTHS_0_2);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2026, 4, 15))).isEqualTo(3);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 4, 15))).isEqualTo(AgeBand.MONTHS_3_8);
    }

    @Test
    void shouldClassifyEightMonthsBeforeNineMonthTransition() {
        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2026, 9, 15))).isEqualTo(8);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 9, 15))).isEqualTo(AgeBand.MONTHS_3_8);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2026, 10, 14))).isEqualTo(8);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 10, 14))).isEqualTo(AgeBand.MONTHS_3_8);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2026, 10, 15))).isEqualTo(9);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 10, 15))).isEqualTo(AgeBand.MONTHS_9_12);
    }

    @Test
    void shouldClassifyTwelveMonthsBeforeThirteenMonthTransition() {
        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2027, 1, 15))).isEqualTo(12);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2027, 1, 15))).isEqualTo(AgeBand.MONTHS_9_12);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2027, 2, 14))).isEqualTo(12);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2027, 2, 14))).isEqualTo(AgeBand.MONTHS_9_12);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2027, 2, 15))).isEqualTo(13);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2027, 2, 15))).isEqualTo(AgeBand.MONTHS_13_24);
    }

    @Test
    void shouldClassifyTwentyFourMonthsBeforeTwentyFiveMonthTransition() {
        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2028, 1, 15))).isEqualTo(24);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2028, 1, 15))).isEqualTo(AgeBand.MONTHS_13_24);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2028, 2, 14))).isEqualTo(24);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2028, 2, 14))).isEqualTo(AgeBand.MONTHS_13_24);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2028, 2, 15))).isEqualTo(25);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2028, 2, 15))).isEqualTo(AgeBand.MONTHS_25_36);
    }

    @Test
    void shouldClassifyThirtySixMonthsBeforeThirtySevenMonthTransition() {
        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2029, 1, 15))).isEqualTo(36);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2029, 1, 15))).isEqualTo(AgeBand.MONTHS_25_36);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2029, 2, 14))).isEqualTo(36);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2029, 2, 14))).isEqualTo(AgeBand.MONTHS_25_36);

        assertThat(AgePolicy.completedMonths(BIRTH, LocalDate.of(2029, 2, 15))).isEqualTo(37);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2029, 2, 15))).isEqualTo(AgeBand.MONTHS_37_PLUS);
    }

    @Test
    void shouldCompleteMonthOnlyOnBirthDayOfMonth() {
        LocalDate birth = LocalDate.of(2026, 3, 15);

        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2026, 4, 14))).isZero();
        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2026, 4, 15))).isEqualTo(1);
    }

    @Test
    void shouldResolveAllNextTransitionsFromBirthDate() {
        assertThat(AgePolicy.nextBand(BIRTH, LocalDate.of(2026, 1, 15)))
                .contains(AgeBand.MONTHS_3_8);
        assertThat(AgePolicy.nextTransitionDate(BIRTH, LocalDate.of(2026, 1, 15)))
                .contains(LocalDate.of(2026, 4, 15));

        assertThat(AgePolicy.nextBand(BIRTH, LocalDate.of(2026, 6, 15)))
                .contains(AgeBand.MONTHS_9_12);
        assertThat(AgePolicy.nextTransitionDate(BIRTH, LocalDate.of(2026, 6, 15)))
                .contains(LocalDate.of(2026, 10, 15));

        assertThat(AgePolicy.nextBand(BIRTH, LocalDate.of(2026, 12, 15)))
                .contains(AgeBand.MONTHS_13_24);
        assertThat(AgePolicy.nextTransitionDate(BIRTH, LocalDate.of(2026, 12, 15)))
                .contains(LocalDate.of(2027, 2, 15));

        assertThat(AgePolicy.nextBand(BIRTH, LocalDate.of(2027, 6, 15)))
                .contains(AgeBand.MONTHS_25_36);
        assertThat(AgePolicy.nextTransitionDate(BIRTH, LocalDate.of(2027, 6, 15)))
                .contains(LocalDate.of(2028, 2, 15));

        assertThat(AgePolicy.nextBand(BIRTH, LocalDate.of(2028, 6, 15)))
                .contains(AgeBand.MONTHS_37_PLUS);
        assertThat(AgePolicy.nextTransitionDate(BIRTH, LocalDate.of(2028, 6, 15)))
                .contains(LocalDate.of(2029, 2, 15));
    }

    @Test
    void shouldReturnEmptyNextTransitionOnLastBand() {
        LocalDate reference = LocalDate.of(2029, 2, 15);

        assertThat(AgePolicy.classify(BIRTH, reference)).isEqualTo(AgeBand.MONTHS_37_PLUS);
        assertThat(AgePolicy.nextBand(BIRTH, reference)).isEmpty();
        assertThat(AgePolicy.nextTransitionDate(BIRTH, reference)).isEmpty();
    }

    @Test
    void shouldDeriveBandFromReferenceDateNotFromToday() {
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2026, 4, 15))).isEqualTo(AgeBand.MONTHS_3_8);
        assertThat(AgePolicy.classify(BIRTH, LocalDate.of(2028, 6, 15))).isEqualTo(AgeBand.MONTHS_25_36);
    }

    @Test
    void shouldHandleEndOfJanuaryBirthDate() {
        LocalDate birth = LocalDate.of(2026, 1, 31);

        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2026, 2, 28))).isZero();
        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2026, 3, 31))).isEqualTo(2);
    }

    @Test
    void shouldHandleLeapDayBirthDate() {
        LocalDate birth = LocalDate.of(2024, 2, 29);

        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2024, 3, 29))).isEqualTo(1);
        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2025, 2, 28))).isEqualTo(11);
        assertThat(AgePolicy.classify(birth, LocalDate.of(2025, 2, 28))).isEqualTo(AgeBand.MONTHS_9_12);
    }

    @Test
    void shouldHandleYearTurnover() {
        LocalDate birth = LocalDate.of(2025, 12, 15);

        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2026, 1, 14))).isZero();
        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2026, 1, 15))).isEqualTo(1);
    }

    @Test
    void shouldRejectNullDates() {
        LocalDate reference = LocalDate.of(2026, 4, 15);

        assertThatThrownBy(() -> AgePolicy.completedMonths(null, reference))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgePolicy.completedMonths(BIRTH, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgePolicy.classify(null, reference))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgePolicy.classify(BIRTH, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgePolicy.nextBand(null, reference))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgePolicy.nextBand(BIRTH, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgePolicy.nextTransitionDate(null, reference))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgePolicy.nextTransitionDate(BIRTH, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldReturnFirstConsistentTransitionDateForEndOfMonthBirthDate() {
        LocalDate birth = LocalDate.of(2026, 1, 31);
        LocalDate reference = LocalDate.of(2026, 3, 31);

        assertThat(AgePolicy.classify(birth, reference)).isEqualTo(AgeBand.MONTHS_0_2);

        LocalDate transition = AgePolicy.nextTransitionDate(birth, reference).orElseThrow();

        assertThat(transition).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(AgePolicy.completedMonths(birth, transition.minusDays(1))).isEqualTo(2);
        assertThat(AgePolicy.completedMonths(birth, transition)).isEqualTo(3);
        assertThat(AgePolicy.classify(birth, transition.minusDays(1))).isEqualTo(AgeBand.MONTHS_0_2);
        assertThat(AgePolicy.classify(birth, transition)).isEqualTo(AgeBand.MONTHS_3_8);
    }

    @Test
    void shouldReturnConsistentLaterTransitionForEndOfMonthBirthDate() {
        LocalDate birth = LocalDate.of(2026, 1, 31);
        LocalDate reference = LocalDate.of(2027, 1, 31);

        assertThat(AgePolicy.classify(birth, reference)).isEqualTo(AgeBand.MONTHS_9_12);

        LocalDate transition = AgePolicy.nextTransitionDate(birth, reference).orElseThrow();

        assertThat(transition).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(AgePolicy.completedMonths(birth, transition.minusDays(1))).isEqualTo(12);
        assertThat(AgePolicy.completedMonths(birth, transition)).isEqualTo(13);
        assertThat(AgePolicy.classify(birth, transition.minusDays(1))).isEqualTo(AgeBand.MONTHS_9_12);
        assertThat(AgePolicy.classify(birth, transition)).isEqualTo(AgeBand.MONTHS_13_24);
    }

    @Test
    void shouldReturnConsistentTransitionForLeapDayBirthDate() {
        LocalDate birth = LocalDate.of(2024, 2, 29);
        LocalDate reference = LocalDate.of(2025, 2, 28);

        assertThat(AgePolicy.classify(birth, reference)).isEqualTo(AgeBand.MONTHS_9_12);

        LocalDate transition = AgePolicy.nextTransitionDate(birth, reference).orElseThrow();

        assertThat(transition).isEqualTo(LocalDate.of(2025, 3, 29));
        assertThat(AgePolicy.completedMonths(birth, transition.minusDays(1))).isEqualTo(12);
        assertThat(AgePolicy.completedMonths(birth, transition)).isEqualTo(13);
        assertThat(AgePolicy.classify(birth, transition.minusDays(1))).isEqualTo(AgeBand.MONTHS_9_12);
        assertThat(AgePolicy.classify(birth, transition)).isEqualTo(AgeBand.MONTHS_13_24);
    }

    @Test
    void shouldKeepTransitionInvariantForEveryBoundaryAndBirthDay() {
        int[] boundaries = {3, 9, 13, 25, 37};
        AgeBand[] previousBands = {
                AgeBand.MONTHS_0_2,
                AgeBand.MONTHS_3_8,
                AgeBand.MONTHS_9_12,
                AgeBand.MONTHS_13_24,
                AgeBand.MONTHS_25_36
        };
        AgeBand[] nextBands = {
                AgeBand.MONTHS_3_8,
                AgeBand.MONTHS_9_12,
                AgeBand.MONTHS_13_24,
                AgeBand.MONTHS_25_36,
                AgeBand.MONTHS_37_PLUS
        };
        LocalDate[] births = {
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2024, 5, 31),
                LocalDate.of(2024, 2, 29)
        };

        for (LocalDate birth : births) {
            for (int i = 0; i < boundaries.length; i++) {
                int boundary = boundaries[i];
                LocalDate reference = birth.plusMonths(boundary - 1);

                assertThat(AgePolicy.classify(birth, reference))
                        .as("birth %s boundary %s", birth, boundary)
                        .isEqualTo(previousBands[i]);

                LocalDate transition = AgePolicy.nextTransitionDate(birth, reference).orElseThrow();

                assertThat(AgePolicy.completedMonths(birth, transition.minusDays(1)))
                        .as("birth %s boundary %s day before", birth, boundary)
                        .isLessThan(boundary);
                assertThat(AgePolicy.completedMonths(birth, transition))
                        .as("birth %s boundary %s transition", birth, boundary)
                        .isGreaterThanOrEqualTo(boundary);
                assertThat(AgePolicy.classify(birth, transition.minusDays(1)))
                        .as("birth %s boundary %s previous band", birth, boundary)
                        .isEqualTo(previousBands[i]);
                assertThat(AgePolicy.classify(birth, transition))
                        .as("birth %s boundary %s next band", birth, boundary)
                        .isEqualTo(nextBands[i]);
                assertThat(AgePolicy.nextBand(birth, reference))
                        .as("birth %s boundary %s next band", birth, boundary)
                        .contains(nextBands[i]);
            }
        }
    }

    @Test
    void shouldRejectBirthDateAfterReferenceDate() {
        LocalDate birth = LocalDate.of(2026, 4, 15);
        LocalDate reference = LocalDate.of(2026, 4, 14);

        assertThatThrownBy(() -> AgePolicy.completedMonths(birth, reference))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgePolicy.classify(birth, reference))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgePolicy.nextBand(birth, reference))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgePolicy.nextTransitionDate(birth, reference))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReachThresholdOnSameDayForNormalBirthDate() {
        assertThat(AgePolicy.reachesMonthsOn(BIRTH, 3)).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(AgePolicy.reachesMonthsOn(BIRTH, 9)).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(AgePolicy.reachesMonthsOn(BIRTH, 13)).isEqualTo(LocalDate.of(2027, 2, 15));
    }

    @Test
    void shouldReachThreeMonthsOnFirstOfMayForEndOfJanuaryBirthDate() {
        LocalDate birth = LocalDate.of(2026, 1, 31);

        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2026, 4, 30))).isEqualTo(2);

        LocalDate threshold = AgePolicy.reachesMonthsOn(birth, 3);

        assertThat(threshold).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(AgePolicy.completedMonths(birth, threshold.minusDays(1))).isEqualTo(2);
        assertThat(AgePolicy.completedMonths(birth, threshold)).isEqualTo(3);
    }

    @Test
    void shouldReachClampedThresholdsForEndOfJanuaryBirthDate() {
        LocalDate birth = LocalDate.of(2026, 1, 31);

        assertThat(AgePolicy.reachesMonthsOn(birth, 1)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(AgePolicy.reachesMonthsOn(birth, 13)).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void shouldReachThresholdPastClampedFebruaryForLeapDayBirthDate() {
        LocalDate birth = LocalDate.of(2024, 2, 29);

        assertThat(AgePolicy.completedMonths(birth, LocalDate.of(2025, 2, 28))).isEqualTo(11);

        LocalDate threshold = AgePolicy.reachesMonthsOn(birth, 12);

        assertThat(threshold).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(AgePolicy.completedMonths(birth, threshold.minusDays(1))).isEqualTo(11);
        assertThat(AgePolicy.completedMonths(birth, threshold)).isEqualTo(12);
    }

    @Test
    void shouldReturnBirthDateForZeroMonths() {
        assertThat(AgePolicy.reachesMonthsOn(BIRTH, 0)).isEqualTo(BIRTH);
    }

    @Test
    void shouldRejectNegativeMonths() {
        assertThatThrownBy(() -> AgePolicy.reachesMonthsOn(BIRTH, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullBirthDate() {
        assertThatThrownBy(() -> AgePolicy.reachesMonthsOn(null, 3))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldKeepReachesMonthsOnInvariantForThresholdsAndBirthDays() {
        int[] thresholds = {0, 1, 3, 9, 13, 25, 37};
        LocalDate[] births = {
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2024, 5, 31),
                LocalDate.of(2024, 2, 29)
        };

        for (LocalDate birth : births) {
            for (int threshold : thresholds) {
                LocalDate date = AgePolicy.reachesMonthsOn(birth, threshold);

                assertThat(AgePolicy.completedMonths(birth, date))
                        .as("birth %s threshold %s", birth, threshold)
                        .isGreaterThanOrEqualTo(threshold);
                if (threshold > 0) {
                    assertThat(AgePolicy.completedMonths(birth, date.minusDays(1)))
                            .as("birth %s threshold %s day before", birth, threshold)
                            .isLessThan(threshold);
                } else {
                    assertThat(date).as("birth %s threshold 0", birth).isEqualTo(birth);
                }
            }
        }
    }

    @Test
    void shouldStayConsistentWithNextTransitionDate() {
        int[] boundaries = {3, 9, 13, 25, 37};
        LocalDate[] births = {
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2024, 2, 29)
        };

        for (LocalDate birth : births) {
            for (int boundary : boundaries) {
                LocalDate date = AgePolicy.reachesMonthsOn(birth, boundary);
                LocalDate reference = date.minusMonths(1);

                assertThat(AgePolicy.nextTransitionDate(birth, reference))
                        .as("birth %s boundary %s", birth, boundary)
                        .contains(date);
            }
        }
    }
}
