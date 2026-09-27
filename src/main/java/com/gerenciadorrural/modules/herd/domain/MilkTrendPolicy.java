package com.gerenciadorrural.modules.herd.domain;

import java.math.BigDecimal;

public final class MilkTrendPolicy {
  private MilkTrendPolicy() {}

  public static MilkTrend classify(BigDecimal latest, BigDecimal average, long records) {
    if (latest == null || average == null || average.signum() <= 0 || records < 2) {
      return MilkTrend.INSUFFICIENT_DATA;
    }
    if (latest.compareTo(average.multiply(new BigDecimal("0.85"))) <= 0) {
      return MilkTrend.DOWN;
    }
    if (latest.compareTo(average.multiply(new BigDecimal("1.15"))) >= 0) {
      return MilkTrend.UP;
    }
    return MilkTrend.STABLE;
  }
}
