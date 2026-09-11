package com.gerenciadorrural.modules.herd.domain;
import com.gerenciadorrural.shared.tenancy.TenantId; import java.time.LocalDate; import java.util.*;
public interface AnimalEventRepository {
 void record(TenantId tenantId, UUID farmId, UUID animalId, AnimalEventType type, UUID operationId, UUID actorUserId, LocalDate occurredOn, long resultingVersion, AnimalEventDetails details);
 Optional<AnimalEvent> findByOperation(TenantId tenantId, UUID farmId, UUID operationId);
 List<AnimalEvent> history(TenantId tenantId, UUID farmId, UUID animalId, AnimalEventType type, int size, long offset);
 long count(TenantId tenantId, UUID farmId, UUID animalId, AnimalEventType type);
 void lockOperation(TenantId tenantId, UUID farmId, UUID operationId);
 default void recordMovement(TenantId tenantId, UUID farmId, UUID animalId, UUID operationId, UUID actorUserId, LocalDate occurredOn, long resultingVersion, MovedEventDetails details) { record(tenantId,farmId,animalId,AnimalEventType.MOVED,operationId,actorUserId,occurredOn,resultingVersion,details); }
}
