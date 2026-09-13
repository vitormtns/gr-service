package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate; import java.util.UUID;
public record BreedingEventDetails(UUID pregnancyId,ReproductionServiceType serviceType,LocalDate serviceOn,LocalDate expectedCalvingOn,String sireReference,String notes) implements AnimalEventDetails {}
