package com.gerenciadorrural.modules.inventory.domain;
import java.math.BigDecimal; import java.time.*; import java.util.*;
public final class InventoryModels { private InventoryModels(){} public enum Unit {UNIT,KG,G,L,ML,M,M2,M3} public enum Status {ACTIVE,INACTIVE} public enum Type {RECEIPT,ISSUE,ADJUSTMENT_IN,ADJUSTMENT_OUT,TRANSFER}
 public record Product(UUID id,String name,String code,String category,Unit baseUnit,Status status,long version,Instant createdAt,Instant updatedAt){}
 public record Location(UUID id,String name,String code,Status status,long version,Instant createdAt,Instant updatedAt){}
 public record Balance(UUID productId,String productName,Unit baseUnit,UUID locationId,String locationName,BigDecimal quantity,long version,Instant updatedAt){}
 public record Movement(UUID id,UUID operationId,Type type,UUID productId,String productName,Unit baseUnit,UUID sourceLocationId,String sourceLocationName,UUID destinationLocationId,String destinationLocationName,BigDecimal quantity,BigDecimal sourceBalanceAfter,BigDecimal destinationBalanceAfter,LocalDate occurredOn,UUID actorUserId,String notes,Instant recordedAt){}
}
