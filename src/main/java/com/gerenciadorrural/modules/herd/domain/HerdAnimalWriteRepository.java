package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;

import java.util.Optional;
import java.util.UUID;

/** Porta interna de escrita e leitura de replay; não representa um endpoint por ID. */
public interface HerdAnimalWriteRepository {

    HerdAnimalSummary insert(NewHerdAnimal animal);

    Optional<HerdAnimalSummary> findById(TenantId tenantId, UUID farmId, UUID id);
}
