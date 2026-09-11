package com.gerenciadorrural.modules.herd.domain;
import java.util.UUID;
public record MovedEventDetails(UUID sourcePaddockId, String sourcePaddockName, UUID destinationPaddockId, String destinationPaddockName, String notes) implements AnimalEventDetails {}
