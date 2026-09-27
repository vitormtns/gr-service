package com.gerenciadorrural.modules.herd.domain;

import java.util.UUID;

public record MotherCorrectedEventDetails(UUID beforeMotherId, UUID afterMotherId,
        long expectedVersion) implements AnimalEventDetails {}
