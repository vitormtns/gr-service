package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HerdGroupRepository {
  Optional<HerdGroup> find(TenantId tenant, UUID farm, UUID id, boolean lock);
  HerdGroup insert(TenantId tenant, UUID farm, HerdGroup group);
  Optional<HerdGroup> update(TenantId tenant, UUID farm, HerdGroup group, long expectedVersion);
  List<HerdGroup> list(TenantId tenant, UUID farm, int limit, long offset);
  long count(TenantId tenant, UUID farm);
  boolean animalExists(TenantId tenant, UUID farm, UUID animal);
  boolean memberExists(TenantId tenant, UUID farm, UUID group, UUID animal);
  void addMember(TenantId tenant, UUID farm, UUID group, UUID animal);
  void removeMember(TenantId tenant, UUID farm, UUID group, UUID animal);
  Optional<HerdGroup> bumpVersion(TenantId tenant, UUID farm, UUID group, long expectedVersion);
  HerdAnimalPage animals(TenantId tenant, UUID farm, HerdGroup group, LocalDate reference,
      int page, int size);
}
