package com.gerenciadorrural.modules.farms.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.util.UUID;
public record FarmProfile(UUID id,TenantId organizationId,String name,String status,long version) { }
