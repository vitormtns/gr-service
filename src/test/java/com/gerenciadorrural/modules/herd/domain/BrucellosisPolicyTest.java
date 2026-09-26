package com.gerenciadorrural.modules.herd.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrucellosisPolicyTest {

    private static final LocalDate BIRTH = LocalDate.of(2026, 1, 15);

    @Test
    void shouldStayBeforeMandatoryWindowBelowThreeMonths() {
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 1, 15)))
                .isEqualTo(BrucellosisWindow.BEFORE_MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 3, 15)))
                .isEqualTo(BrucellosisWindow.BEFORE_MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 4, 14)))
                .isEqualTo(BrucellosisWindow.BEFORE_MANDATORY_WINDOW);
    }

    @Test
    void shouldEnterMandatoryWindowAtThreeMonths() {
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 4, 15)))
                .isEqualTo(BrucellosisWindow.MANDATORY_WINDOW);
    }

    @Test
    void shouldKeepMandatoryWindowThroughEightMonths() {
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 7, 15)))
                .isEqualTo(BrucellosisWindow.MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 8, 15)))
                .isEqualTo(BrucellosisWindow.MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 9, 15)))
                .isEqualTo(BrucellosisWindow.MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 10, 14)))
                .isEqualTo(BrucellosisWindow.MANDATORY_WINDOW);
    }

    @Test
    void shouldLeaveMandatoryWindowAtNineMonths() {
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2026, 10, 15)))
                .isEqualTo(BrucellosisWindow.AFTER_MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2027, 1, 15)))
                .isEqualTo(BrucellosisWindow.AFTER_MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, LocalDate.of(2029, 2, 15)))
                .isEqualTo(BrucellosisWindow.AFTER_MANDATORY_WINDOW);
    }

    @Test
    void shouldNotApplyToNonFemaleRegardlessOfAge() {
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.MALE, BIRTH, LocalDate.of(2026, 3, 15)))
                .isEqualTo(BrucellosisWindow.NOT_APPLICABLE);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.MALE, BIRTH, LocalDate.of(2026, 6, 15)))
                .isEqualTo(BrucellosisWindow.NOT_APPLICABLE);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.MALE, BIRTH, LocalDate.of(2027, 1, 15)))
                .isEqualTo(BrucellosisWindow.NOT_APPLICABLE);
    }

    @Test
    void shouldFollowAgePolicyCalendarSemanticsForEndOfMonthBirthDate() {
        LocalDate birth = LocalDate.of(2026, 1, 31);

        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, birth, LocalDate.of(2026, 4, 30)))
                .isEqualTo(BrucellosisWindow.BEFORE_MANDATORY_WINDOW);
        assertThat(BrucellosisPolicy.window(HerdAnimalSex.FEMALE, birth, LocalDate.of(2026, 5, 1)))
                .isEqualTo(BrucellosisWindow.MANDATORY_WINDOW);
    }

    @Test
    void shouldRejectNullInputs() {
        LocalDate reference = LocalDate.of(2026, 4, 15);

        assertThatThrownBy(() -> BrucellosisPolicy.window(null, BIRTH, reference))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BrucellosisPolicy.window(HerdAnimalSex.FEMALE, null, reference))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BrucellosisPolicy.window(HerdAnimalSex.FEMALE, BIRTH, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectBirthDateAfterReferenceDate() {
        LocalDate birth = LocalDate.of(2026, 4, 15);
        LocalDate reference = LocalDate.of(2026, 4, 14);

        assertThatThrownBy(() -> BrucellosisPolicy.window(HerdAnimalSex.FEMALE, birth, reference))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
