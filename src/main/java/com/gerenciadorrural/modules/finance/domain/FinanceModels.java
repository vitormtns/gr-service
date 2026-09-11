package com.gerenciadorrural.modules.finance.domain;
import java.math.BigDecimal; import java.time.*; import java.util.*;
public final class FinanceModels {private FinanceModels(){} public enum CategoryKind{INCOME,EXPENSE,BOTH} public enum CategoryStatus{ACTIVE,INACTIVE} public enum EntryType{INCOME,EXPENSE} public enum EntryStatus{PENDING,SETTLED,CANCELLED} public enum EventType{CREATED,CORRECTED,SETTLED,CANCELLED}
 public record Category(UUID id,String name,String code,CategoryKind kind,CategoryStatus status,long version,Instant createdAt,Instant updatedAt){}
 public record CategorySummary(UUID id,String name,CategoryKind kind){}
 public record Entry(UUID id,UUID operationId,EntryType type,EntryStatus status,CategorySummary category,String description,BigDecimal amount,LocalDate dueOn,LocalDate settledOn,String notes,long version,Instant createdAt,Instant updatedAt){}
 public record Event(UUID id,UUID operationId,EventType type,UUID actorUserId,LocalDate occurredOn,Object details,Instant recordedAt){}
 public record Summary(BigDecimal pendingIncome,BigDecimal pendingExpense,BigDecimal settledIncome,BigDecimal settledExpense,BigDecimal netSettled,BigDecimal overdueIncome,BigDecimal overdueExpense){}
 public record CategoryTotals(CategorySummary category,BigDecimal settledIncome,BigDecimal settledExpense,BigDecimal pendingIncome,BigDecimal pendingExpense){}
}
