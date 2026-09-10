package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalPatch;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import java.time.LocalDate;

public record CorrectCurrentFarmAnimalCommand(
        Long expectedVersion,
        HerdAnimalPatch<String> identification,
        HerdAnimalPatch<String> name,
        HerdAnimalPatch<HerdAnimalSex> sex,
        HerdAnimalPatch<LocalDate> birthDate
) { }
