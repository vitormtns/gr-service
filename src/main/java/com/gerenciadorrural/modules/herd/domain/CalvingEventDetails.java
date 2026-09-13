package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate; import java.util.UUID;
public record CalvingEventDetails(UUID pregnancyId,UUID calfAnimalId,LocalDate calvedOn) implements AnimalEventDetails {}
