package com.gerenciadorrural.modules.herd.domain;
public record HerdAnimalQuery(String search,HerdAnimalSex sex,HerdAnimalStatus status,int page,int size) {}
