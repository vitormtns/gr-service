package com.gerenciadorrural.modules.farms.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.util.Optional; import java.util.UUID;
public interface FarmProfileQueryRepository { Optional<FarmProfile> findCurrent(TenantId tenantId, UUID farmId); }
