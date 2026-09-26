package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.math.BigDecimal; import java.time.*; import java.util.*;
public interface HerdManagementRepository {
 Optional<String> operation(TenantId t,UUID farm,UUID op); void saveOperation(TenantId t,UUID farm,UUID op,String type,String payload);
 void weight(TenantId t,UUID farm,UUID animal,UUID op,LocalDate day,BigDecimal kg,String notes,UUID actor);
 void treatment(TenantId t,UUID farm,UUID animal,UUID op,HealthTreatmentType type,HealthProcedureCode procedureCode,LocalDate day,String product,String protocol,LocalDate due,String notes,UUID actor);
 List<Weight> weights(TenantId t,UUID farm,UUID animal,int size,long offset); long weightCount(TenantId t,UUID farm,UUID animal);
 List<Treatment> treatments(TenantId t,UUID farm,UUID animal,int size,long offset); long treatmentCount(TenantId t,UUID farm,UUID animal);
 default Optional<Treatment> findTreatment(TenantId t,UUID farm,UUID animal,UUID treatment){throw new UnsupportedOperationException();}
 default List<Pending> pending(TenantId t,UUID farm,LocalDate reference,int weighingDays,int upcomingDays,PendingWorkType type,UUID animal,int size,long offset){throw new UnsupportedOperationException();}
 default long pendingCount(TenantId t,UUID farm,LocalDate reference,int weighingDays,int upcomingDays,PendingWorkType type,UUID animal){throw new UnsupportedOperationException();}
 default List<BrucellosisPendingCandidate> brucellosisPendingCandidates(TenantId t,UUID farm,LocalDate reference,UUID animal){throw new UnsupportedOperationException();}
 default HealthTreatmentRetraction insertHealthTreatmentRetraction(TenantId t,UUID farm,UUID animal,UUID treatment,UUID op,String reason,UUID actor){throw new UnsupportedOperationException();}
 default Optional<HealthTreatmentRetraction> findHealthTreatmentRetractionByOperation(TenantId t,UUID farm,UUID op){throw new UnsupportedOperationException();}
 default Optional<HealthTreatmentRetraction> findHealthTreatmentRetractionByTreatment(TenantId t,UUID farm,UUID animal,UUID treatment){throw new UnsupportedOperationException();}
 record Weight(UUID id,UUID operationId,LocalDate measuredOn,BigDecimal weightKg,String notes,UUID actorUserId,Instant recordedAt){}
 record Treatment(UUID id,UUID operationId,HealthTreatmentType type,HealthProcedureCode procedureCode,LocalDate occurredOn,String product,String protocol,LocalDate nextDueOn,String notes,UUID actorUserId,Instant recordedAt){}
 record Pending(PendingWorkType type,UUID animalId,String identification,String name,UUID farmId,LocalDate date,UUID pregnancyId,HealthTreatmentType treatmentType,LocalDate lastPerformedOn,LocalDate lastWeightOn){}
 record BrucellosisPendingCandidate(UUID animalId,String identification,String name,UUID farmId,HerdAnimalSex sex,LocalDate birthDate,List<Treatment> treatments){}
 record HealthTreatmentRetraction(UUID id,UUID operationId,UUID animalId,UUID treatmentId,String reason,UUID actorUserId,Instant retractedAt){}
 final class HealthTreatmentRetractionWriteConflictException extends RuntimeException {
  public enum Type { OPERATION, TARGET }
  private final Type type;
  public HealthTreatmentRetractionWriteConflictException(Type type,Throwable cause){super(cause);this.type=type;}
  public Type type(){return type;}
 }
}
