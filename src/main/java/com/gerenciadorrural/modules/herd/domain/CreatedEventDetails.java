package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;

public record CreatedEventDetails(String identification, String name, String sex, LocalDate birthDate) implements AnimalEventDetails {
}
