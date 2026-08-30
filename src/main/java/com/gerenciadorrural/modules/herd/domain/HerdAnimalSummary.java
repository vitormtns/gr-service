package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate; import java.util.UUID;
public record HerdAnimalSummary(UUID id,String identification,String name,HerdAnimalSex sex,LocalDate birthDate,HerdAnimalStatus status,long version) {}
