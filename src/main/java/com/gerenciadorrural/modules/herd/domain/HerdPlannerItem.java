package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HerdPlannerItem(
    UUID id,
    TenantId tenantId,
    UUID farmId,
    UUID animalId,
    HerdPlannerType type,
    String title,
    String notes,
    LocalDate scheduledFor,
    HerdPlannerStatus status,
    long version,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    Instant cancelledAt) {}
