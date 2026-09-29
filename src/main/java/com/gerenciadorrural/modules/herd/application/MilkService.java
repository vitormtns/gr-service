package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.domain.MilkRecordRepository.*;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MilkService {
  private static final Set<String> WRITE = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");
  private static final Set<String> READ = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR", "VIEWER");
  private final TenantTransactionExecutor transactions;
  private final HerdAnimalProfileRepository animals;
  private final AnimalEventRepository events;
  private final MilkRecordRepository records;
  private final Clock clock;

  public MilkService(TenantTransactionExecutor transactions, HerdAnimalProfileRepository animals,
      AnimalEventRepository events, MilkRecordRepository records, Clock clock) {
    this.transactions = transactions;
    this.animals = animals;
    this.events = events;
    this.records = records;
    this.clock = clock;
  }

  public MilkRecord record(TenantContext context, UUID animalId, Command command) {
    if (!WRITE.contains(context.role())) {
      throw new MilkForbiddenException();
    }
    if (animalId == null || command == null || command.operationId() == null
        || command.expectedVersion() == null || command.expectedVersion() < 0
        || command.recordedOn() == null
        || command.recordedOn().isAfter(LocalDate.now(clock)) || command.liters() == null
        || command.liters().signum() <= 0 || command.liters().scale() > 3
        || command.liters().precision() > 9 || command.liters().precision() - command.liters().scale() > 6) {
      throw new HerdAnimalCommandInvalidException();
    }
    String notes = notes(command.notes());
    BigDecimal liters = command.liters().stripTrailingZeros();
    MilkRecordedEventDetails details = new MilkRecordedEventDetails(liters, command.session(), notes);
    return transactions.execute(context, () -> {
      events.lockOperation(context.tenantId(), context.farmId(), command.operationId());
      var prior = events.findByOperation(context.tenantId(), context.farmId(), command.operationId());
      if (prior.isPresent()) {
        AnimalEvent event = prior.get();
        if (!event.animalId().equals(animalId) || event.type() != AnimalEventType.MILK_RECORDED
            || !command.recordedOn().equals(event.occurredOn())
            || !Objects.equals(event.details(), details)
            || event.resultingVersion() - 1 != command.expectedVersion()) {
          throw new HerdOperationIdempotencyConflictException();
        }
        return records.byOperation(context.tenantId(), context.farmId(), command.operationId())
            .orElseThrow(HerdOperationIdempotencyConflictException::new);
      }
      HerdAnimalSummary animal = animals.findByIdForCorrection(context.tenantId(),
          context.farmId(), animalId).orElseThrow(HerdAnimalNotFoundException::new);
      if (animal.version() != command.expectedVersion()) {
        throw new HerdAnimalVersionConflictException();
      }
      if (animal.status() != HerdAnimalStatus.ACTIVE || animal.sex() != HerdAnimalSex.FEMALE) {
        throw new HerdLifecycleConflictException();
      }
      if (animal.birthDate() != null && command.recordedOn().isBefore(animal.birthDate())) {
        throw new HerdAnimalCommandInvalidException();
      }
      HerdAnimalSummary updated = animals.touch(context.tenantId(), context.farmId(),
          animalId, command.expectedVersion()).orElseThrow(HerdAnimalVersionConflictException::new);
      MilkRecord saved = records.insert(context.tenantId(), context.farmId(), animalId,
          command.operationId(), command.recordedOn(), liters, command.session(), notes,
          context.userId());
      events.record(context.tenantId(), context.farmId(), animalId,
          AnimalEventType.MILK_RECORDED, command.operationId(), context.userId(),
          command.recordedOn(), updated.version(), details);
      return saved;
    });
  }

  public Page<MilkRecord> history(TenantContext context, UUID animalId, int page, int size) {
    read(context);
    if (animalId == null || page < 0 || size < 1 || size > 100
        || (long) page * size > Integer.MAX_VALUE) {
      throw new HerdReportQueryInvalidException();
    }
    return transactions.execute(context, () -> {
      exists(context, animalId);
      return new Page<>(records.history(context.tenantId(), context.farmId(), animalId,
          size, (long) page * size), page, size,
          records.count(context.tenantId(), context.farmId(), animalId));
    });
  }

  public MilkOverview overview(TenantContext context, LocalDate referenceDate) {
    read(context);
    LocalDate reference = reference(referenceDate);
    return transactions.execute(context,
        () -> records.overview(context.tenantId(), context.farmId(), reference));
  }

  public AnimalSummary animalSummary(TenantContext context, UUID animalId,
      LocalDate referenceDate) {
    read(context);
    if (animalId == null) {
      throw new HerdReportQueryInvalidException();
    }
    LocalDate reference = reference(referenceDate);
    return transactions.execute(context, () -> {
      exists(context, animalId);
      MilkAnimalStats stats = records.animalStats(context.tenantId(), context.farmId(),
          animalId, reference);
      MilkTrend trend = MilkTrendPolicy.classify(
          stats.lastRecord() == null ? null : stats.lastRecord().liters(),
          stats.averageLitersLast7Days(), stats.recordsLast7Days());
      return new AnimalSummary(stats.lastRecord(), stats.averageLitersLast7Days(),
          stats.recordsLast7Days(), trend);
    });
  }

  private LocalDate reference(LocalDate date) {
    LocalDate today = LocalDate.now(clock);
    if (date != null && date.isAfter(today)) {
      throw new HerdReportQueryInvalidException();
    }
    return date == null ? today : date;
  }

  private static void read(TenantContext context) {
    if (!READ.contains(context.role())) {
      throw new MilkForbiddenException();
    }
  }

  private void exists(TenantContext context, UUID animalId) {
    if (animals.findById(context.tenantId(), context.farmId(), animalId).isEmpty()) {
      throw new HerdAnimalNotFoundException();
    }
  }

  private static String notes(String value) {
    if (value == null) {
      return null;
    }
    String normalized = PosixEdgeWhitespace.trim(value);
    if (normalized.isEmpty() || normalized.indexOf('\0') >= 0
        || normalized.codePointCount(0, normalized.length()) > 1000) {
      throw new HerdAnimalCommandInvalidException();
    }
    return normalized;
  }

  public record Command(UUID operationId, Long expectedVersion, LocalDate recordedOn,
      BigDecimal liters, MilkSession session, String notes) {}

  public record Page<T>(List<T> items, int page, int size, long totalElements) {}

  public record AnimalSummary(MilkRecord lastRecord, BigDecimal averageLitersLast7Days,
      long recordsLast7Days, MilkTrend trend) {}
}
