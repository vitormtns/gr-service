package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate;
public record HealthTreatmentEventDetails(HealthTreatmentType treatmentType,HealthProcedureCode procedureCode,String product,String protocol,LocalDate nextDueOn,String notes) implements AnimalEventDetails {}
