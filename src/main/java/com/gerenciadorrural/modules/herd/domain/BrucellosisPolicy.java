package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;
import java.util.Objects;

public final class BrucellosisPolicy {

    private BrucellosisPolicy() {
    }

    public static BrucellosisWindow window(HerdAnimalSex sex, LocalDate birthDate, LocalDate referenceDate) {
        Objects.requireNonNull(sex, "O sexo é obrigatório");
        if (sex != HerdAnimalSex.FEMALE) {
            return BrucellosisWindow.NOT_APPLICABLE;
        }
        int months = AgePolicy.completedMonths(birthDate, referenceDate);
        if (months < 3) {
            return BrucellosisWindow.BEFORE_MANDATORY_WINDOW;
        }
        if (months <= 8) {
            return BrucellosisWindow.MANDATORY_WINDOW;
        }
        return BrucellosisWindow.AFTER_MANDATORY_WINDOW;
    }
}
