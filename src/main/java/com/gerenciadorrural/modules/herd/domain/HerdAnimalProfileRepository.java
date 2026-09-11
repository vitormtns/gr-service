package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Porta tenant-aware para o perfil individual e sua correção concorrente. */
public interface HerdAnimalProfileRepository {

    Optional<HerdAnimalSummary> findById(TenantId tenantId, UUID farmId, UUID id);

    /** Serializa a decisão de no-op com correções concorrentes sem executar UPDATE. */
    Optional<HerdAnimalSummary> findByIdForCorrection(TenantId tenantId, UUID farmId, UUID id);

    Optional<HerdAnimalSummary> update(
            TenantId tenantId,
            UUID farmId,
            UUID id,
            long expectedVersion,
            String identification,
            String name,
            HerdAnimalSex sex,
            LocalDate birthDate
    );
    default Optional<HerdAnimalSummary> updateStatus(TenantId tenantId, UUID farmId, UUID id, long expectedVersion, HerdAnimalStatus status) { throw new UnsupportedOperationException(); }
    default Optional<HerdAnimalSummary> updatePaddock(TenantId tenantId, UUID farmId, UUID id, long expectedVersion, UUID paddockId) { throw new UnsupportedOperationException(); }
    default Optional<HerdAnimalSummary> transfer(TenantId tenantId, UUID sourceFarmId, UUID destinationFarmId, UUID id, long expectedVersion, UUID destinationPaddockId) { throw new UnsupportedOperationException(); }
}
