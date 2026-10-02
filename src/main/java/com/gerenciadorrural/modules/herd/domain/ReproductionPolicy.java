package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Historical BovNex estimate, measured in calendar days from the recorded service. */
public final class ReproductionPolicy {
    public static final int GESTATION_DAYS = 283;

    private ReproductionPolicy() {}

    public static LocalDate expectedCalvingOn(LocalDate serviceOn) {
        return Objects.requireNonNull(serviceOn).plusDays(GESTATION_DAYS);
    }
}
