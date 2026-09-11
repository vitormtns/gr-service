package com.gerenciadorrural.modules.herd.domain;
import java.util.UUID;
public record TransferredEventDetails(UUID sourceFarmId,String sourceFarmName,UUID destinationFarmId,String destinationFarmName,UUID destinationPaddockId,String destinationPaddockName,String notes) implements AnimalEventDetails {}
