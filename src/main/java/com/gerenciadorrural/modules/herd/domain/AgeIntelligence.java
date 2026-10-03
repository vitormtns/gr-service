package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Projeção derivada; não representa estado persistido nem aptidão de manejo. */
public record AgeIntelligence(LocalDate birthDate, LocalDate referenceDate, int completedMonths,
    AgeBand currentBand, AgeBand nextBand, LocalDate transitionOn, Long daysUntilTransition,
    Integer boundaryMonths, String policy, long completedDays) {
  public static AgeIntelligence derive(LocalDate birthDate, LocalDate referenceDate) {
    if (birthDate == null || birthDate.isAfter(referenceDate)) return null;
    var next = AgePolicy.nextBand(birthDate, referenceDate).orElse(null);
    var transition = AgePolicy.nextTransitionDate(birthDate, referenceDate).orElse(null);
    return new AgeIntelligence(birthDate, referenceDate,
        AgePolicy.completedMonths(birthDate, referenceDate), AgePolicy.classify(birthDate, referenceDate),
        next, transition, transition == null ? null : ChronoUnit.DAYS.between(referenceDate, transition),
        next == null ? null : AgePolicy.minimumMonths(next), "COMPLETED_CALENDAR_MONTHS_V1",
        ChronoUnit.DAYS.between(birthDate, referenceDate));
  }

  public boolean withinHorizon(int horizonDays) {
    if (horizonDays < 0) throw new IllegalArgumentException("O horizonte não pode ser negativo");
    return daysUntilTransition != null && daysUntilTransition >= 0 && daysUntilTransition <= horizonDays;
  }
}
