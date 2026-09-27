package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaternalRelationRepository {
    record Relation(UUID motherId, UUID pregnancyId) {}

    void lockFarm(TenantId tenantId, UUID farmId);

    void insert(TenantId tenantId, UUID motherId, UUID calfId, UUID pregnancyId);

    Optional<UUID> motherId(TenantId tenantId, UUID calfId);

    Optional<Relation> relation(TenantId tenantId, UUID calfId);

    List<UUID> calfIds(TenantId tenantId, UUID motherId, int size, long offset);

    boolean change(TenantId tenantId, UUID calfId, UUID beforeMotherId, UUID afterMotherId);

    boolean remove(TenantId tenantId, UUID calfId, UUID beforeMotherId);
}
