package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;

public record CreateCurrentFarmAnimalResult(Outcome outcome, HerdAnimalSummary animal) {

    public enum Outcome {
        CREATED,
        REPLAYED
    }
}
