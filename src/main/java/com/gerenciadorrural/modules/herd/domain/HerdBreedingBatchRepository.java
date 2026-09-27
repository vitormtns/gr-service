package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HerdBreedingBatchRepository {
    record Receipt(String payloadHash, List<UUID> motherIds, List<UUID> pregnancyIds) {}

    void lock(TenantId tenantId, UUID farmId, UUID operationId);

    Optional<Receipt> find(TenantId tenantId, UUID farmId, UUID operationId);

    void save(TenantId tenantId, UUID farmId, UUID operationId, Receipt receipt);
}
