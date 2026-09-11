package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.util.*;
public interface MovementOperationRepository { Optional<String> payload(TenantId tenantId, UUID farmId, UUID operationId); void save(TenantId tenantId, UUID farmId, UUID operationId, String canonicalPayload); List<PaddockMovement> movements(TenantId tenantId, UUID farmId, UUID paddockId, int size, long offset); long countMovements(TenantId tenantId, UUID farmId, UUID paddockId); }
