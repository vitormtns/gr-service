package com.gerenciadorrural.modules.finance.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.gerenciadorrural.modules.finance.domain.FinanceModels.*;

public interface FinanceRepository {
 void lockOperation(TenantId tenantId, UUID farmId, UUID operationId);
 Optional<Category> category(TenantId tenantId, UUID id, boolean lock);
 Category insertCategory(TenantId tenantId, Category category);
 Category updateCategory(TenantId tenantId, Category category, long version);
 List<Category> categories(TenantId tenantId);
 Optional<Entry> entry(TenantId tenantId, UUID farmId, UUID id, boolean lock);
 Optional<Entry> entryByOperation(TenantId tenantId, UUID farmId, UUID operationId);
 Entry insertEntry(TenantId tenantId, UUID farmId, Entry entry, UUID userId);
 Entry updateEntry(TenantId tenantId, UUID farmId, Entry entry, long version, UUID userId);
 void event(TenantId tenantId, UUID farmId, UUID entryId, UUID operationId, EventType type, UUID actorId, LocalDate occurredOn, Object details);
 Optional<Event> eventByOperation(TenantId tenantId, UUID farmId, UUID entryId, UUID operationId);
 List<Event> history(TenantId tenantId, UUID farmId, UUID entryId);
 List<Entry> entries(TenantId tenantId, UUID farmId, EntryType type, EntryStatus status, UUID categoryId, LocalDate from, LocalDate to, String search, int size, long offset);
 long count(TenantId tenantId, UUID farmId, EntryType type, EntryStatus status, UUID categoryId, LocalDate from, LocalDate to, String search);
 Summary summary(TenantId tenantId, UUID farmId, UUID categoryId, LocalDate from, LocalDate to, LocalDate today);
 List<CategoryTotals> categoryTotals(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to);
}
