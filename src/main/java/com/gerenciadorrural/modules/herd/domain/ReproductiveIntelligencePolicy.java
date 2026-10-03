package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Atenção operacional derivada; não representa diagnóstico ou aptidão fisiológica. */
public final class ReproductiveIntelligencePolicy {
  private ReproductiveIntelligencePolicy() {}
  public enum Level { UPCOMING, DUE_TODAY, OVERDUE, OVERDUE_ATTENTION, OVERDUE_EXTENDED }
  public record CalvingAttention(Level level,LocalDate expectedOn,LocalDate referenceDate,
      long daysUntil,long daysOverdue,String guidance) {}
  public record Postpartum(LocalDate calvedOn,LocalDate referenceDate,long daysSinceCalving,
      LocalDate reviewOn,long daysUntilReview,boolean reviewDue,int reviewAfterDays,String guidance) {}
  public static CalvingAttention calving(LocalDate expectedOn,LocalDate referenceDate) {
    long days=ChronoUnit.DAYS.between(expectedOn,referenceDate);
    Level level=days<0?Level.UPCOMING:days==0?Level.DUE_TODAY:days<=6?Level.OVERDUE
        :days<=13?Level.OVERDUE_ATTENTION:Level.OVERDUE_EXTENDED;
    String guidance=switch(level) {
      case UPCOMING -> "Acompanhe a matriz e prepare o manejo para a data prevista.";
      case DUE_TODAY -> "Confira a matriz e os sinais de parto. Registre somente fatos observados.";
      case OVERDUE -> "Confira a matriz e o histórico. A data prevista não comprova que o parto ocorreu.";
      case OVERDUE_ATTENTION -> "Priorize a verificação da matriz e do acompanhamento reprodutivo.";
      case OVERDUE_EXTENDED -> "Revise a previsão e procure orientação veterinária para avaliar a matriz.";
    };
    return new CalvingAttention(level,expectedOn,referenceDate,Math.max(0,-days),Math.max(0,days),guidance);
  }
  public static Postpartum postpartum(LocalDate calvedOn,LocalDate referenceDate,int reviewAfterDays) {
    if(reviewAfterDays<1||calvedOn.isAfter(referenceDate))
      throw new IllegalArgumentException("As datas de acompanhamento pós-parto são inválidas");
    LocalDate review=calvedOn.plusDays(reviewAfterDays);
    long until=ChronoUnit.DAYS.between(referenceDate,review);
    return new Postpartum(calvedOn,referenceDate,ChronoUnit.DAYS.between(calvedOn,referenceDate),
        review,Math.max(0,until),until<=0,reviewAfterDays,
        until>0?"Acompanhe a recuperação e planeje a avaliação reprodutiva. O prazo não determina aptidão para cobertura."
            :"O marco de acompanhamento foi atingido. Planeje uma avaliação reprodutiva; a aptidão exige avaliação individual.");
  }
}
