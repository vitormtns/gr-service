package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HerdAnimalImportRepository {
    record Receipt(String payloadHash, List<UUID> animalIds) {}

    void lock(TenantId tenantId, UUID farmId, UUID operationId);

    Optional<Receipt> find(TenantId tenantId, UUID farmId, UUID operationId);

    void save(TenantId tenantId, UUID farmId, UUID operationId, Receipt receipt);

    Optional<HerdAnimalSummary> findByIdentification(TenantId tenantId, UUID farmId, String identification);

    Optional<UUID> motherId(TenantId tenantId, UUID animalId);

    void linkMother(TenantId tenantId, UUID motherId, UUID calfId);
}
