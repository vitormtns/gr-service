package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HerdAgeIntelligenceRepository {
  List<Animal> transitions(TenantId tenantId, UUID farmId, LocalDate referenceDate,
      int horizonDays, int size, long offset);
  long transitionCount(TenantId tenantId, UUID farmId, LocalDate referenceDate, int horizonDays);
  record Animal(UUID animalId, String identification, String name, LocalDate birthDate) {}
}
