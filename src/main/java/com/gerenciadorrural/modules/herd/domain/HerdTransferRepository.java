package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.time.*; import java.util.*;
public interface HerdTransferRepository {
 record Farm(UUID id,String name) {}
 record Row(UUID id,UUID animalId,UUID operationId,UUID sourceFarmId,UUID destinationFarmId,String sourceFarmName,String destinationFarmName,UUID destinationPaddockId,String destinationPaddockName,LocalDate occurredOn,Instant recordedAt,UUID actorUserId,long originalExpectedVersion,long resultingVersion,String notes) {}
 void lockOperation(TenantId tenantId,UUID operationId); Optional<String> payload(TenantId tenantId,UUID operationId); void saveOperation(TenantId tenantId,UUID operationId,String payload);
 Optional<Farm> lockFarm(TenantId tenantId,UUID farmId); boolean canAccessFarm(TenantId tenantId,UUID membershipId,String scopeMode,UUID farmId);
 boolean identificationExists(TenantId tenantId,UUID farmId,String identification,UUID excludedAnimalId);
 void insert(Row row,TenantId tenantId); List<Row> byOperation(TenantId tenantId,UUID operationId); Optional<Row> detail(TenantId tenantId,UUID farmId,UUID id); Page page(TenantId tenantId,UUID farmId,String direction,UUID animalId,int size,long offset);
 record Page(List<Row> items,long total) {}
}
