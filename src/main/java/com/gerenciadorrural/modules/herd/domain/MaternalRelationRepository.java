package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.util.*;
public interface MaternalRelationRepository {void insert(TenantId tenantId,UUID motherId,UUID calfId,UUID pregnancyId); Optional<UUID> motherId(TenantId tenantId,UUID calfId); List<UUID> calfIds(TenantId tenantId,UUID motherId,int size,long offset);}
