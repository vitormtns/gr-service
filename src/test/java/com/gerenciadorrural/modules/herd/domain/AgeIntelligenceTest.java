package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AgeIntelligenceTest {
  @Test void newbornAgeRetainsExactDaysFromBackendFacts() {
    var birth=LocalDate.of(2026,9,30);
    assertThat(AgeIntelligence.derive(birth,birth).completedDays()).isZero();
    assertThat(AgeIntelligence.derive(birth,birth.plusDays(1)).completedDays()).isEqualTo(1);
    var at=AgeIntelligence.derive(birth,LocalDate.of(2026,10,14));
    assertThat(at.completedMonths()).isZero();
    assertThat(at.completedDays()).isEqualTo(14);
  }
  @Test void explainsEveryBoundaryBeforeOnAndAfterItsCalendarTransition() {
    for (var birth : new LocalDate[]{LocalDate.of(2026,1,15), LocalDate.of(2026,1,31),
        LocalDate.of(2024,2,29), LocalDate.of(2024,12,31)}) {
      for (int boundary : AgePolicy.transitionBoundaries()) {
        var transition = AgePolicy.reachesMonthsOn(birth, boundary);
        var before = AgeIntelligence.derive(birth, transition.minusDays(1));
        assertThat(before.boundaryMonths()).isEqualTo(boundary);
        assertThat(before.transitionOn()).isEqualTo(transition);
        assertThat(before.daysUntilTransition()).isEqualTo(1L);
        assertThat(before.currentBand()).isEqualTo(AgePolicy.classify(birth, transition.minusDays(1)));
        var on = AgeIntelligence.derive(birth, transition);
        assertThat(on.currentBand()).isEqualTo(before.nextBand());
        assertThat(on.completedMonths()).isEqualTo(boundary);
        assertThat(AgeIntelligence.derive(birth, transition.plusDays(1)).currentBand()).isEqualTo(on.currentBand());
        assertThat(on.withinHorizon(0)).isFalse(); // Já mudou; a próxima mudança é futura.
      }
    }
  }

  @Test void horizonIsInclusiveAndHistoricalReferenceDoesNotDependOnToday() {
    var birth = LocalDate.of(2023,9,14);
    var reference = LocalDate.of(2026,10,2);
    var intelligence = AgeIntelligence.derive(birth, reference);
    assertThat(intelligence.completedMonths()).isEqualTo(36);
    assertThat(intelligence.currentBand()).isEqualTo(AgeBand.MONTHS_25_36);
    assertThat(intelligence.transitionOn()).isEqualTo(LocalDate.of(2026,10,14));
    assertThat(intelligence.daysUntilTransition()).isEqualTo(12L);
    for (int days : new int[]{0,1,14,15,16}) {
      var at = AgeIntelligence.derive(birth, intelligence.transitionOn().minusDays(days));
      if (days == 0) { assertThat(at.nextBand()).isNull(); continue; }
      assertThat(at.withinHorizon(days)).isTrue();
      assertThat(at.withinHorizon(days-1)).isFalse();
    }
    assertThat(AgeIntelligence.derive(birth, reference)).isEqualTo(intelligence);
    assertThat(AgeIntelligence.derive(birth, LocalDate.of(2025,1,1)).completedMonths()).isEqualTo(15);
  }

  @Test void unknownOrFutureBirthIsUnavailableAndFinalBandHasNoTransition() {
    var reference = LocalDate.of(2026,10,2);
    assertThat(AgeIntelligence.derive(null, reference)).isNull();
    assertThat(AgeIntelligence.derive(reference.plusDays(1), reference)).isNull();
    var mature = AgeIntelligence.derive(LocalDate.of(2020,1,1), reference);
    assertThat(mature.currentBand()).isEqualTo(AgeBand.MONTHS_37_PLUS);
    assertThat(mature.nextBand()).isNull();
    assertThat(mature.transitionOn()).isNull();
    assertThat(mature.boundaryMonths()).isNull();
    assertThat(mature.withinHorizon(366)).isFalse();
    assertThatThrownBy(() -> mature.withinHorizon(-1)).isInstanceOf(IllegalArgumentException.class);
  }
}
