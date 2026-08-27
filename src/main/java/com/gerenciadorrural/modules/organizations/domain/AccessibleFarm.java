package com.gerenciadorrural.modules.organizations.domain;

import java.util.Objects;
import java.util.UUID;

public record AccessibleFarm(UUID farmId, String farmName) {

    public AccessibleFarm {
        Objects.requireNonNull(farmId, "farmId é obrigatório");
        Objects.requireNonNull(farmName, "farmName é obrigatório");
    }
}
