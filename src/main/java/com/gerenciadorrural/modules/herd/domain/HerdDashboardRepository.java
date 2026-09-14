package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Porta de leitura do dashboard operacional pecuário da Phase 10C. */
public interface HerdDashboardRepository {

  HerdSnapshot snapshot(
      TenantId tenantId,
      UUID farmId,
      HerdReportCategory category,
      HerdAnimalSex sex,
      UUID paddockId);

  ActivityTotals activity(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to);

  List<ActivityBucket> activitySeries(
      TenantId tenantId, UUID farmId, LocalDate from, LocalDate to);

  AttentionSummary attention(
      TenantId tenantId,
      UUID farmId,
      LocalDate referenceDate,
      int weighingDueDays,
      int calvingUpcomingDays);

  ReproductionPipeline reproductionPipeline(
      TenantId tenantId, UUID farmId, LocalDate referenceDate, int calvingUpcomingDays);

  record PaddockTotal(UUID id, String name, long total) {}

  record HerdSnapshot(
      long activeAnimals,
      Map<HerdAnimalSex, Long> bySex,
      Map<HerdReportCategory, Long> byCategory,
      List<PaddockTotal> byPaddock,
      long unlocatedAnimals) {}

  record ActivityTotals(
      long births,
      long deaths,
      long sales,
      long movements,
      long transfersIn,
      long transfersOut,
      long weightMeasurements,
      long vaccinations,
      long dewormings,
      long breedings,
      long pregnanciesConfirmed,
      long pregnanciesTerminated,
      long calvings,
      long calvesBorn,
      long plannerCompleted,
      long plannerCancelled) {}

  record ActivityBucket(
      LocalDate date,
      long births,
      long deaths,
      long sales,
      long movements,
      long weights,
      long healthTreatments,
      long breedings,
      long calvings) {}

  record AttentionSummary(
      long vaccinationDue,
      long dewormingDue,
      long weighingDue,
      long calvingUpcoming,
      long calvingOverdue,
      long plannerOpen,
      long plannerOverdue) {}

  record ReproductionPipeline(long openPossiblePregnancies, long openConfirmedPregnancies) {}
}
