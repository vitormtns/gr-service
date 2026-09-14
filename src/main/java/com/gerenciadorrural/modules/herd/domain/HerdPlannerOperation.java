package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

public record HerdPlannerOperation(
    TenantId tenantId,
    UUID farmId,
    UUID operationId,
    Type type,
    UUID plannerItemId,
    String payload,
    long resultingVersion,
    Instant recordedAt) {
  public enum Type {
    CREATE,
    CORRECT,
    COMPLETE,
    CANCEL
  }
}
