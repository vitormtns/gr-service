package com.gerenciadorrural.modules.herd.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MilkTrendPolicyTest {
  @Test
  void requiresTwoRecentRecordsAndClassifiesThresholds() {
    assertThat(MilkTrendPolicy.classify(new BigDecimal("20"), null, 2))
        .isEqualTo(MilkTrend.INSUFFICIENT_DATA);
    assertThat(MilkTrendPolicy.classify(new BigDecimal("20"), new BigDecimal("20"), 1))
        .isEqualTo(MilkTrend.INSUFFICIENT_DATA);
    assertThat(MilkTrendPolicy.classify(new BigDecimal("8.5"), new BigDecimal("10"), 2))
        .isEqualTo(MilkTrend.DOWN);
    assertThat(MilkTrendPolicy.classify(new BigDecimal("11.5"), new BigDecimal("10"), 2))
        .isEqualTo(MilkTrend.UP);
    assertThat(MilkTrendPolicy.classify(new BigDecimal("10"), new BigDecimal("10"), 2))
        .isEqualTo(MilkTrend.STABLE);
  }
}
