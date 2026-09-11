package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.*;
public interface PaddockRepository {
 Optional<PaddockSummary> find(TenantId tenantId, UUID farmId, UUID id);
 Optional<PaddockSummary> lock(TenantId tenantId, UUID farmId, UUID id);
 Optional<PaddockSummary> create(TenantId tenantId, UUID farmId, PaddockSummary paddock);
 Optional<PaddockSummary> update(TenantId tenantId, UUID farmId, PaddockSummary paddock, long expectedVersion);
 PaddockPage list(TenantId tenantId, UUID farmId, PaddockStatus status, String search, int page, int size);
 long occupancy(TenantId tenantId, UUID farmId, UUID id);
 Map<HerdAnimalStatus,Long> occupancyByStatus(TenantId tenantId, UUID farmId, UUID id);
 HerdAnimalPage animals(TenantId tenantId, UUID farmId, UUID id, int page, int size);
}
