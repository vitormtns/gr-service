package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.*;

public interface AnimalLineageRepository {
  List<Node> lineage(TenantId tenantId, UUID farmId, UUID animalId, int depth, int limit);
  record Node(UUID animalId, String identification, String name, java.time.LocalDate birthDate, HerdAnimalSex sex,
      String direction, int generation, UUID relatedToAnimalId, boolean hasFurtherRelations) {}
}
