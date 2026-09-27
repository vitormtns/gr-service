package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.SaleChannel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LifecycleCurrentFarmAnimalCommand(
    UUID operationId, Long expectedVersion, LocalDate occurredOn, String notes,
    String deathReason, SaleChannel saleChannel, String saleBuyer, BigDecimal saleAmount) {
  public LifecycleCurrentFarmAnimalCommand(
      UUID operationId, Long expectedVersion, LocalDate occurredOn, String notes) {
    this(operationId, expectedVersion, occurredOn, notes, null, null, null, null);
  }
}
