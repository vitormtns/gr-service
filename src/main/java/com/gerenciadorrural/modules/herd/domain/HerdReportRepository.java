package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Portas explícitas dos relatórios pecuários da Phase 10B. */
public interface HerdReportRepository {

  ReportPage<HerdPositionSummary, HerdPositionItem> herdPosition(
      TenantId tenantId,
      UUID farmId,
      HerdReportCategory category,
      HerdAnimalSex sex,
      UUID paddockId,
      int limit,
      long offset);

  List<AgeSexCount> currentAgeSexCounts(TenantId tenantId, UUID farmId);

  List<AgeSexCount> historicalAgeSexCounts(TenantId tenantId, UUID farmId, LocalDate asOf);

  List<ProcedureAgeSexCount> currentProcedureAgeSexCounts(TenantId tenantId, UUID farmId,
      HealthProcedureCode procedureCode);

  EventLedger eventLedger(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to);

  List<AgeSexFlow> ageSexFlows(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to);

  AgeSexAnimals ageSexAnimals(TenantId tenantId, UUID farmId, LocalDate referenceDate,
      boolean historical, HerdAnimalSex sex, LocalDate birthAfter, LocalDate birthThrough,
      boolean unknownBirthDate, int limit, long offset);

  record AgeSexFlow(HerdAnimalSex sex, LocalDate birthDate, LocalDate occurredOn,
      AnimalEventType eventType, long count) {}

  record AgeSexAnimal(AnimalReference animal, HerdAnimalSex sex, LocalDate birthDate,
      boolean availableInCurrentFarm) {}

  record AgeSexAnimals(List<AgeSexAnimal> items, long totalElements) {}

  ReportPage<LifecycleSummary, LifecycleItem> lifecycle(
      TenantId tenantId,
      UUID farmId,
      LocalDate from,
      LocalDate to,
      LifecycleEvent event,
      UUID animalId,
      int limit,
      long offset);

  ReportPage<MovementSummary, MovementItem> movements(
      TenantId tenantId,
      UUID farmId,
      LocalDate from,
      LocalDate to,
      UUID animalId,
      UUID sourcePaddockId,
      UUID destinationPaddockId,
      int limit,
      long offset);

  ReportPage<TransferSummary, TransferItem> transfers(
      TenantId tenantId,
      UUID farmId,
      LocalDate from,
      LocalDate to,
      TransferDirection direction,
      UUID animalId,
      int limit,
      long offset);

  ReportPage<WeightSummary, WeightItem> weights(
      TenantId tenantId,
      UUID farmId,
      LocalDate from,
      LocalDate to,
      UUID animalId,
      HerdReportCategory category,
      int limit,
      long offset);

  ReportPage<HealthSummary, HealthItem> health(
      TenantId tenantId,
      UUID farmId,
      LocalDate from,
      LocalDate to,
      HealthTreatmentType treatmentType,
      HealthProcedureCode procedureCode,
      UUID animalId,
      int limit,
      long offset);

  ReportPage<ReproductionSummary, ReproductionItem> reproduction(
      TenantId tenantId,
      UUID farmId,
      LocalDate from,
      LocalDate to,
      UUID motherId,
      ReproductionServiceType serviceType,
      PregnancyStatus pregnancyStatus,
      int limit,
      long offset);

  ReportPage<PlannerSummary, PlannerItem> planner(
      TenantId tenantId,
      UUID farmId,
      LocalDate from,
      LocalDate to,
      LocalDate referenceDate,
      HerdPlannerStatus status,
      HerdPlannerType type,
      UUID animalId,
      int limit,
      long offset);

  enum LifecycleEvent {
    CREATED,
    BORN,
    SOLD,
    DECEASED
  }

  enum TransferDirection {
    IN,
    OUT,
    ALL
  }

  enum ReproductionAction {
    BREEDING_RECORDED,
    PREGNANCY_CONFIRMED,
    PREGNANCY_TERMINATED,
    CALVED,
    BORN
  }

  record ReportPage<S, I>(S summary, List<I> items, long totalElements) {}

  record AnimalReference(UUID id, String identification, String name) {}

  record PaddockReference(UUID id, String name) {}

  record FarmReference(UUID id, String name) {}

  record PaddockTotal(PaddockReference paddock, long total) {}

  record AgeSexCount(HerdAnimalSex sex, LocalDate birthDate, long count) {}

  record ProcedureAgeSexCount(HerdAnimalSex sex, LocalDate birthDate,
      boolean withRecordedTreatment, long count) {}

  record EventLedger(long openingAnimals, long registeredAnimals, long births,
      long transfersIn, long sales, long deaths, long transfersOut, long closingAnimals) {}

  record HerdPositionSummary(
      long totalActiveAnimals,
      Map<HerdReportCategory, Long> totalsByCategory,
      Map<HerdAnimalSex, Long> totalsBySex,
      List<PaddockTotal> totalsByPaddock,
      long unlocatedCount) {}

  record HerdPositionItem(
      AnimalReference animal,
      HerdReportCategory category,
      HerdAnimalSex sex,
      LocalDate birthDate,
      PaddockReference paddock) {}

  record LifecycleSummary(Map<LifecycleEvent, Long> countsByEventType,
      long totalAffectedAnimals, BigDecimal totalSaleAmount, BigDecimal averageSaleAmount,
      long salesWithAmount, Map<String, Long> deathsByReason,
      Map<String, Long> salesByChannel) {}

  record LifecycleItem(
      UUID id,
      AnimalReference animal,
      LifecycleEvent event,
      LocalDate occurredOn,
      Instant recordedAt,
      String notes,
      String deathReason,
      SaleChannel saleChannel,
      String saleBuyer,
      BigDecimal saleAmount) {}

  record MovementSummary(long movementCount, long distinctAnimalsMoved) {}

  record MovementItem(
      UUID id,
      AnimalReference animal,
      LocalDate occurredOn,
      Instant recordedAt,
      PaddockReference sourcePaddock,
      PaddockReference destinationPaddock,
      String notes) {}

  record TransferSummary(long transferCount, long inboundCount, long outboundCount, long distinctAnimalsTransferred) {}

  record TransferItem(
      UUID id,
      AnimalReference animal,
      TransferDirection direction,
      LocalDate occurredOn,
      Instant recordedAt,
      FarmReference sourceFarm,
      FarmReference destinationFarm,
      PaddockReference destinationPaddock,
      String notes) {}

  record WeightSummary(
      long measurementCount,
      long animalsMeasured,
      BigDecimal averageWeight,
      BigDecimal minimumWeight,
      BigDecimal maximumWeight) {}

  record WeightItem(
      UUID id,
      AnimalReference animal,
      LocalDate occurredOn,
      Instant recordedAt,
      BigDecimal weight) {}

  record HealthSummary(
      long treatmentsCount,
      long animalsTreated,
      Map<HealthTreatmentType, Long> countsByTreatmentType) {}

  record HealthItem(
      UUID id,
      AnimalReference animal,
      HealthTreatmentType treatmentType,
      HealthProcedureCode procedureCode,
      LocalDate occurredOn,
      Instant recordedAt,
      String product,
      String protocol,
      LocalDate nextDueOn) {}

  record ReproductionSummary(
      long servicesRecorded,
      long pregnanciesConfirmed,
      long pregnanciesTerminated,
      long calvings,
      long calvesBorn,
      long openPossiblePregnancies,
      long openConfirmedPregnancies) {}

  record ReproductionItem(
      UUID id,
      AnimalReference mother,
      ReproductionAction action,
      LocalDate occurredOn,
      Instant recordedAt,
      UUID pregnancyId,
      ReproductionServiceType serviceType,
      LocalDate expectedCalvingOn,
      UUID calfId,
      PregnancyTerminationReason terminationReason) {}

  record PlannerSummary(long open, long completed, long cancelled, long overdueOpen) {}

  record PlannerItem(
      UUID id,
      HerdPlannerType type,
      String title,
      String notes,
      LocalDate scheduledFor,
      HerdPlannerStatus status,
      AnimalReference animal,
      long version,
      Instant createdAt,
      Instant updatedAt) {}
}
