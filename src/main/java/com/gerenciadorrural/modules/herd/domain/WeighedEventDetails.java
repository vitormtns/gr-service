package com.gerenciadorrural.modules.herd.domain;
import java.math.BigDecimal;
public record WeighedEventDetails(BigDecimal weightKg,String notes) implements AnimalEventDetails {}
