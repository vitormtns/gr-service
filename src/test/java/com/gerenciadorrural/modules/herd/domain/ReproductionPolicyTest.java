package com.gerenciadorrural.modules.herd.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReproductionPolicyTest {
    @Test void usesExactly283DaysAcrossCalendarBoundaries() {
        assertThat(ReproductionPolicy.expectedCalvingOn(LocalDate.parse("2026-01-15")))
                .isEqualTo(LocalDate.parse("2026-10-25"));
        assertThat(ReproductionPolicy.expectedCalvingOn(LocalDate.parse("2026-01-31")))
                .isEqualTo(LocalDate.parse("2026-11-10"));
        assertThat(ReproductionPolicy.expectedCalvingOn(LocalDate.parse("2025-12-31")))
                .isEqualTo(LocalDate.parse("2026-10-10"));
        assertThat(ReproductionPolicy.expectedCalvingOn(LocalDate.parse("2024-02-29")))
                .isEqualTo(LocalDate.parse("2024-12-08"));
    }
}
