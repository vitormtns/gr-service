package com.gerenciadorrural.modules.herd.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReproductiveIntelligencePolicyTest {
  private final LocalDate expected=LocalDate.of(2026,10,2);
  @Test void respectsEveryTemporalBoundaryWithoutInventingBirth() {
    long[] days={-30,-7,-1,0,1,6,7,13,14,15};
    String[] levels={"UPCOMING","UPCOMING","UPCOMING","DUE_TODAY","OVERDUE","OVERDUE",
        "OVERDUE_ATTENTION","OVERDUE_ATTENTION","OVERDUE_EXTENDED","OVERDUE_EXTENDED"};
    for(int i=0;i<days.length;i++) {
      var attention=ReproductiveIntelligencePolicy.calving(expected,expected.plusDays(days[i]));
      assertThat(attention.level().name()).isEqualTo(levels[i]);
      assertThat(attention.daysOverdue()).isEqualTo(Math.max(0,days[i]));
      assertThat(attention.daysUntil()).isEqualTo(Math.max(0,-days[i]));
      assertThat(attention.guidance()).doesNotContain("quase certo","impossível","pronta");
    }
  }
  @Test void derivesPostpartumOnlyFromRecordedDateAndConfiguredReview() {
    for(int day:new int[]{0,1,44,45,46}) {
      var result=ReproductiveIntelligencePolicy.postpartum(expected,expected.plusDays(day),45);
      assertThat(result.reviewOn()).isEqualTo(expected.plusDays(45));
      assertThat(result.daysSinceCalving()).isEqualTo(day);
      assertThat(result.reviewDue()).isEqualTo(day>=45);
      assertThat(result.daysUntilReview()).isEqualTo(Math.max(0,45-day));
    }
    assertThat(ReproductiveIntelligencePolicy.postpartum(expected,expected.plusDays(30),30).reviewDue()).isTrue();
    assertThatThrownBy(()->ReproductiveIntelligencePolicy.postpartum(expected,expected.minusDays(1),45))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
