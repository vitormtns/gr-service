package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilkRecordRepository {
  MilkRecord insert(TenantId tenantId, UUID farmId, UUID animalId, UUID operationId,
      LocalDate recordedOn, BigDecimal liters, MilkSession session, String notes, UUID actorUserId);

  Optional<MilkRecord> byOperation(TenantId tenantId, UUID farmId, UUID operationId);

  List<MilkRecord> history(TenantId tenantId, UUID farmId, UUID animalId, int size, long offset);

  long count(TenantId tenantId, UUID farmId, UUID animalId);

  MilkOverview overview(TenantId tenantId, UUID farmId, LocalDate referenceDate);

  MilkAnimalStats animalStats(TenantId tenantId, UUID farmId, UUID animalId,
      LocalDate referenceDate);

  record MilkRecord(UUID id, UUID operationId, UUID animalId, LocalDate recordedOn,
      BigDecimal liters, MilkSession session, String notes, UUID actorUserId,
      Instant recordedAt) {}

  record MilkOverview(BigDecimal litersToday, long femalesWithRecordToday,
      BigDecimal averageLitersPerRecordLast7Days) {}

  record MilkAnimalStats(MilkRecord lastRecord, BigDecimal averageLitersLast7Days,
      long recordsLast7Days) {}
}
