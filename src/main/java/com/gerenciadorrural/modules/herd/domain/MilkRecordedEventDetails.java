package com.gerenciadorrural.modules.herd.domain;

import java.math.BigDecimal;

public record MilkRecordedEventDetails(BigDecimal liters, MilkSession session, String notes)
    implements AnimalEventDetails {}
