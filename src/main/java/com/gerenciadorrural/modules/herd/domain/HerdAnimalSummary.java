package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate; import java.util.UUID;
public record HerdAnimalSummary(UUID id,String identification,String name,HerdAnimalSex sex,LocalDate birthDate,HerdAnimalStatus status,long version,PaddockSummary paddock, AgeIntelligence age) {
 public HerdAnimalSummary(UUID id,String identification,String name,HerdAnimalSex sex,LocalDate birthDate,HerdAnimalStatus status,long version,PaddockSummary paddock){this(id,identification,name,sex,birthDate,status,version,paddock,null);}
 public HerdAnimalSummary withAgeAt(LocalDate reference){return new HerdAnimalSummary(id,identification,name,sex,birthDate,status,version,paddock,AgeIntelligence.derive(birthDate,reference));}
 public HerdAnimalSummary(UUID id,String identification,String name,HerdAnimalSex sex,LocalDate birthDate,HerdAnimalStatus status,long version){this(id,identification,name,sex,birthDate,status,version,null);}
}
