package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.util.UUID;
public interface HerdAnimalQueryRepository { HerdAnimalPage list(TenantId tenantId, UUID farmId, HerdAnimalQuery query); }
