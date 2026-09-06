package com.gerenciadorrural.modules.herd.domain;

import java.util.Objects;
import java.util.Optional;

public record HerdAnimalInsertResult(Outcome outcome, Optional<HerdAnimalSummary> animal) {

    public enum Outcome {
        INSERTED,
        ID_ALREADY_EXISTS
    }

    public HerdAnimalInsertResult {
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(animal);
        if ((outcome == Outcome.INSERTED) != animal.isPresent()) {
            throw new IllegalArgumentException("O resultado do INSERT é inconsistente");
        }
    }

    public static HerdAnimalInsertResult inserted(HerdAnimalSummary animal) {
        return new HerdAnimalInsertResult(Outcome.INSERTED, Optional.of(animal));
    }

    public static HerdAnimalInsertResult idAlreadyExists() {
        return new HerdAnimalInsertResult(Outcome.ID_ALREADY_EXISTS, Optional.empty());
    }
}
