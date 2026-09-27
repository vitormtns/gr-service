package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LifecycleCurrentFarmAnimal {
  private final TenantTransactionExecutor transactions;
  private final HerdAnimalProfileRepository animals;
  private final AnimalEventRepository events;
  private final Clock clock;

  public LifecycleCurrentFarmAnimal(TenantTransactionExecutor transactions,
      HerdAnimalProfileRepository animals, AnimalEventRepository events, Clock clock) {
    this.transactions = transactions;
    this.animals = animals;
    this.events = events;
    this.clock = clock;
  }

  public HerdAnimalSummary sale(TenantContext context, UUID animalId,
      LifecycleCurrentFarmAnimalCommand command) {
    return execute(context, animalId, command, AnimalEventType.SOLD,
        Set.of("OWNER", "ADMIN", "MANAGER"));
  }

  public HerdAnimalSummary death(TenantContext context, UUID animalId,
      LifecycleCurrentFarmAnimalCommand command) {
    return execute(context, animalId, command, AnimalEventType.DECEASED,
        Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR"));
  }

  private HerdAnimalSummary execute(TenantContext context, UUID animalId,
      LifecycleCurrentFarmAnimalCommand command, AnimalEventType type, Set<String> roles) {
    if (!roles.contains(context.role())) {
      throw new HerdLifecycleForbiddenException();
    }
    Valid valid = valid(command, type);
    return transactions.execute(context, () -> run(context, animalId, valid, type));
  }

  private HerdAnimalSummary run(TenantContext context, UUID animalId, Valid valid,
      AnimalEventType type) {
    events.lockOperation(context.tenantId(), context.farmId(), valid.operationId());
    var prior = events.findByOperation(context.tenantId(), context.farmId(), valid.operationId());
    if (prior.isPresent()) {
      AnimalEvent event = prior.get();
      if (!event.animalId().equals(animalId) || event.type() != type
          || !Objects.equals(event.occurredOn(), valid.occurredOn())
          || !Objects.equals(event.details(), valid.details())
          || event.resultingVersion() - 1 != valid.expectedVersion()) {
        throw new HerdOperationIdempotencyConflictException();
      }
      return animals.findById(context.tenantId(), context.farmId(), animalId)
          .filter(animal -> animal.version() == event.resultingVersion())
          .orElseThrow(HerdOperationIdempotencyConflictException::new);
    }
    HerdAnimalSummary current = animals.findByIdForCorrection(
        context.tenantId(), context.farmId(), animalId).orElseThrow(HerdAnimalNotFoundException::new);
    if (current.birthDate() != null && valid.occurredOn().isBefore(current.birthDate())) {
      throw new HerdAnimalCommandInvalidException();
    }
    if (current.version() != valid.expectedVersion()) {
      throw new HerdAnimalVersionConflictException();
    }
    if (current.status() != HerdAnimalStatus.ACTIVE) {
      throw new HerdLifecycleConflictException();
    }
    HerdAnimalStatus status = type == AnimalEventType.SOLD
        ? HerdAnimalStatus.SOLD : HerdAnimalStatus.DECEASED;
    HerdAnimalSummary updated = animals.updateStatus(context.tenantId(), context.farmId(),
        animalId, valid.expectedVersion(), status).orElseThrow(HerdAnimalVersionConflictException::new);
    events.record(context.tenantId(), context.farmId(), animalId, type, valid.operationId(),
        context.userId(), valid.occurredOn(), updated.version(), valid.details());
    return updated;
  }

  private Valid valid(LifecycleCurrentFarmAnimalCommand command, AnimalEventType type) {
    if (command == null || command.operationId() == null || command.expectedVersion() == null
        || command.expectedVersion() < 0 || command.occurredOn() == null
        || command.occurredOn().isAfter(LocalDate.now(clock))) {
      throw new HerdAnimalCommandInvalidException();
    }
    String notes = text(command.notes(), 1000);
    String deathReason = text(command.deathReason(), 240);
    String saleBuyer = text(command.saleBuyer(), 240);
    BigDecimal saleAmount = command.saleAmount();
    if (saleAmount != null && (saleAmount.signum() <= 0 || saleAmount.scale() > 2
        || saleAmount.precision() > 19)) {
      throw new HerdAnimalCommandInvalidException();
    }
    if (type == AnimalEventType.SOLD && deathReason != null
        || type == AnimalEventType.DECEASED && (command.saleChannel() != null
            || saleBuyer != null || saleAmount != null)) {
      throw new HerdAnimalCommandInvalidException();
    }
    return new Valid(command.operationId(), command.expectedVersion(), command.occurredOn(),
        new LifecycleEventDetails(notes, deathReason, command.saleChannel(), saleBuyer,
            saleAmount == null ? null : saleAmount.stripTrailingZeros()));
  }

  private static String text(String value, int max) {
    if (value == null) {
      return null;
    }
    String normalized = PosixEdgeWhitespace.trim(value);
    if (normalized.isEmpty() || normalized.indexOf('\0') >= 0
        || normalized.codePointCount(0, normalized.length()) > max) {
      throw new HerdAnimalCommandInvalidException();
    }
    return normalized;
  }

  private record Valid(UUID operationId, long expectedVersion, LocalDate occurredOn,
      LifecycleEventDetails details) {}
}
