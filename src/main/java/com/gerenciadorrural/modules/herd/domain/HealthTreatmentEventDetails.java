package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate;
public record HealthTreatmentEventDetails(HealthTreatmentType treatmentType,String product,String protocol,LocalDate nextDueOn,String notes) implements AnimalEventDetails {}
