package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.AnimalEventRepository;
import com.gerenciadorrural.modules.herd.domain.AnimalEventType;
import com.gerenciadorrural.modules.herd.domain.AnimalEventDetails.HealthTreatmentRetractionEventDetails;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalProfileRepository;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.HealthTreatmentRetraction;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.HealthTreatmentRetractionWriteConflictException;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RetractHealthTreatment {
  private final TenantTransactionExecutor tx;
  private final HerdManagementRepository herd;
  private final HerdAnimalProfileRepository animals;
  private final AnimalEventRepository events;
  private final Clock clock;

  public RetractHealthTreatment(
      TenantTransactionExecutor tx,
      HerdManagementRepository herd,
      HerdAnimalProfileRepository animals,
      AnimalEventRepository events,
      Clock clock) {
    this.tx = tx;
    this.herd = herd;
    this.animals = animals;
    this.events = events;
    this.clock = clock;
  }

  public HealthTreatmentRetraction retract(
      TenantContext context, UUID animalId, UUID treatmentId, UUID operationId, String reason) {
    if (context == null || animalId == null || treatmentId == null || operationId == null) {
      throw new HerdAnimalCommandInvalidException();
    }
    String normalized = normalize(reason);
    return tx.execute(
        context,
        () -> {
          events.lockOperation(context.tenantId(), context.farmId(), operationId);
          var replay =
              herd.findHealthTreatmentRetractionByOperation(
                  context.tenantId(), context.farmId(), operationId);
          if (replay.isPresent()) {
            var existing = replay.get();
            if (!existing.animalId().equals(animalId)
                || !existing.treatmentId().equals(treatmentId)
                || !Objects.equals(existing.reason(), normalized)) {
              throw new HerdOperationIdempotencyConflictException();
            }
            return existing;
          }
          if (events
              .findByOperation(context.tenantId(), context.farmId(), operationId)
              .isPresent()) {
            throw new HerdOperationIdempotencyConflictException();
          }
          var animal =
              animals
                  .findById(context.tenantId(), context.farmId(), animalId)
                  .orElseThrow(HerdAnimalNotFoundException::new);
          var treatment = herd.findTreatment(context.tenantId(), context.farmId(), animalId, treatmentId)
              .orElseThrow(HerdAnimalNotFoundException::new);
          if (operationId.equals(treatment.operationId())) {
            throw new HerdOperationIdempotencyConflictException();
          }
          if (herd
              .findHealthTreatmentRetractionByTreatment(
                  context.tenantId(), context.farmId(), animalId, treatmentId)
              .isPresent()) {
            throw new HerdLifecycleConflictException();
          }
          HealthTreatmentRetraction created;
          try {
            created =
                herd.insertHealthTreatmentRetraction(
                    context.tenantId(),
                    context.farmId(),
                    animalId,
                    treatmentId,
                    operationId,
                    normalized,
                    context.userId());
          } catch (HealthTreatmentRetractionWriteConflictException e) {
            if (e.type() == HealthTreatmentRetractionWriteConflictException.Type.OPERATION) {
              throw new HerdOperationIdempotencyConflictException();
            }
            throw new HerdLifecycleConflictException();
          }
          events.record(
              context.tenantId(),
              context.farmId(),
              animalId,
              AnimalEventType.HEALTH_TREATMENT_RETRACTED,
              operationId,
              context.userId(),
              LocalDate.now(clock),
              animal.version(),
              new HealthTreatmentRetractionEventDetails(treatmentId, created.id(), normalized));
          return created;
        });
  }

  private static String normalize(String reason) {
    if (reason == null) {
      return null;
    }
    String value = reason.strip();
    if (value.isEmpty() || value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > 2000) {
      if (value.isEmpty()) {
        return null;
      }
      throw new HerdAnimalCommandInvalidException();
    }
    return value;
  }
}
