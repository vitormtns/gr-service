package com.gerenciadorrural.modules.herd.domain;
public record HerdAnimalQuery(String search, HerdAnimalSex sex, HerdAnimalStatus status,
                              int page, int size, Boolean unlocated) {
  public HerdAnimalQuery(String search, HerdAnimalSex sex, HerdAnimalStatus status, int page, int size) {
    this(search, sex, status, page, size, null);
  }
}
