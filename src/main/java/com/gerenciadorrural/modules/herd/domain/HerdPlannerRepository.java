package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HerdPlannerRepository {
  Optional<HerdPlannerItem> find(TenantId tenantId, UUID farmId, UUID id);

  HerdPlannerItem insert(HerdPlannerItem item);

  Optional<HerdPlannerItem> update(
      TenantId tenantId,
      UUID farmId,
      UUID id,
      long version,
      HerdPlannerType type,
      String title,
      String notes,
      LocalDate scheduledFor,
      UUID animalId);

  Optional<HerdPlannerItem> transition(
      TenantId tenantId, UUID farmId, UUID id, long version, HerdPlannerStatus status);

  List<HerdPlannerItem> list(
      TenantId tenantId,
      UUID farmId,
      HerdPlannerStatus status,
      HerdPlannerType type,
      UUID animalId,
      LocalDate from,
      LocalDate to,
      int limit,
      long offset);

  long count(
      TenantId tenantId,
      UUID farmId,
      HerdPlannerStatus status,
      HerdPlannerType type,
      UUID animalId,
      LocalDate from,
      LocalDate to);
}
