package com.gerenciadorrural.modules.herd.domain;

import java.util.UUID;

public record HerdGroup(UUID id, String name, Kind kind, Status status, Rules rules, long version) {
  public enum Kind { MANUAL, SMART }
  public enum Status { ACTIVE, ARCHIVED }

  public record Rules(HerdAnimalSex sex, HerdAnimalStatus status, Integer minAgeMonths,
      Integer maxAgeMonths, boolean onlyReproductionActive, boolean onlyMissingProfile) {
    public static Rules empty() {
      return new Rules(null, null, null, null, false, false);
    }
  }
}
