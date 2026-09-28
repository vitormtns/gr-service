package com.gerenciadorrural.modules.herd.domain;

import java.math.BigDecimal;

public record LifecycleEventDetails(
    String notes, String deathReason, SaleChannel saleChannel, String saleBuyer,
    BigDecimal saleAmount) implements AnimalEventDetails {
  public LifecycleEventDetails(String notes) {
    this(notes, null, null, null, null);
  }
}
