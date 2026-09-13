package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate; import java.util.UUID;
public record BornEventDetails(UUID motherAnimalId,UUID pregnancyId,LocalDate bornOn) implements AnimalEventDetails {}
