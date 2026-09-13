package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.Optional;
import java.util.UUID;

public interface HerdPlannerOperationRepository {
  void lock(TenantId tenantId, UUID farmId, UUID operationId);

  Optional<HerdPlannerOperation> find(TenantId tenantId, UUID farmId, UUID operationId);

  void save(HerdPlannerOperation operation);
}
