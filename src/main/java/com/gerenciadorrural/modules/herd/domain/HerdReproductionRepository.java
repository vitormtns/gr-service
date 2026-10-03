package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.time.LocalDate; import java.util.*;
public interface HerdReproductionRepository {
 Optional<Pregnancy> find(TenantId tenantId,UUID farmId,UUID id,boolean lock);
 Optional<Pregnancy> findOpenForMother(TenantId tenantId,UUID farmId,UUID motherId);
 Optional<Pregnancy> findByOperation(TenantId tenantId,UUID farmId,UUID operationId);
 Pregnancy insert(Pregnancy pregnancy);
 Optional<Pregnancy> transition(TenantId tenantId,UUID farmId,UUID id,long expectedVersion,PregnancyStatus status,LocalDate date,PregnancyTerminationReason reason,UUID calfId);
 List<Pregnancy> list(TenantId tenantId,UUID farmId,UUID motherId,int size,long offset);
 long count(TenantId tenantId,UUID farmId,UUID motherId);
 List<FarmPregnancy> listFarm(TenantId tenantId,UUID farmId,PregnancyStatus status,UUID motherId,ReproductionServiceType serviceType,int size,long offset);
 long countFarm(TenantId tenantId,UUID farmId,PregnancyStatus status,UUID motherId,ReproductionServiceType serviceType);
 record FarmPregnancy(Pregnancy pregnancy,String identification,String name) {}
 java.util.Optional<java.time.LocalDate> latestCalvingOn(TenantId tenantId,UUID farmId,UUID motherId);
 record Pregnancy(UUID id,TenantId tenantId,UUID farmId,UUID motherAnimalId,ReproductionServiceType serviceType,LocalDate serviceOn,String sireReference,LocalDate expectedCalvingOn,PregnancyStatus status,LocalDate confirmedOn,LocalDate endedOn,PregnancyTerminationReason terminationReason,UUID calfAnimalId,UUID operationId,String commandPayload,long version) {}
}
