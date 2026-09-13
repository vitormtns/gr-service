package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.math.BigDecimal; import java.time.*; import java.util.*;
public interface HerdManagementRepository {
 Optional<String> operation(TenantId t,UUID farm,UUID op); void saveOperation(TenantId t,UUID farm,UUID op,String type,String payload);
 void weight(TenantId t,UUID farm,UUID animal,UUID op,LocalDate day,BigDecimal kg,String notes,UUID actor);
 void treatment(TenantId t,UUID farm,UUID animal,UUID op,HealthTreatmentType type,LocalDate day,String product,String protocol,LocalDate due,String notes,UUID actor);
 List<Weight> weights(TenantId t,UUID farm,UUID animal,int size,long offset); long weightCount(TenantId t,UUID farm,UUID animal);
 List<Treatment> treatments(TenantId t,UUID farm,UUID animal,int size,long offset); long treatmentCount(TenantId t,UUID farm,UUID animal);
 default List<Pending> pending(TenantId t,UUID farm,LocalDate reference,int weighingDays,int upcomingDays,PendingWorkType type,UUID animal,int size,long offset){throw new UnsupportedOperationException();}
 default long pendingCount(TenantId t,UUID farm,LocalDate reference,int weighingDays,int upcomingDays,PendingWorkType type,UUID animal){throw new UnsupportedOperationException();}
 record Weight(UUID id,UUID operationId,LocalDate measuredOn,BigDecimal weightKg,String notes,UUID actorUserId,Instant recordedAt){}
 record Treatment(UUID id,UUID operationId,HealthTreatmentType type,LocalDate occurredOn,String product,String protocol,LocalDate nextDueOn,String notes,UUID actorUserId,Instant recordedAt){}
 record Pending(PendingWorkType type,UUID animalId,String identification,String name,UUID farmId,LocalDate date,UUID pregnancyId,HealthTreatmentType treatmentType,LocalDate lastPerformedOn,LocalDate lastWeightOn){}
}
