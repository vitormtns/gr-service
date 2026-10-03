package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.*;

public interface GroupSelectionRepository {
  void lockOperation(TenantId tenant, UUID farm, UUID operation);
  Optional<Receipt> receipt(TenantId tenant, UUID farm, UUID operation);
  void saveReceipt(TenantId tenant, UUID farm, UUID operation, UUID group, String command, String result);
  int lockSelectedAnimals(TenantId tenant, UUID farm, List<UUID> animals);
  int addMembers(TenantId tenant, UUID farm, UUID group, List<UUID> animals);
  record Receipt(String command, String result) {}
}
