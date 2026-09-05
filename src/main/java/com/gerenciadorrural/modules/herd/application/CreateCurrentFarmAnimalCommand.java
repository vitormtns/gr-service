package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCurrentFarmAnimalCommand(
        UUID id,
        String identification,
        String name,
        HerdAnimalSex sex,
        LocalDate birthDate
) {
}
