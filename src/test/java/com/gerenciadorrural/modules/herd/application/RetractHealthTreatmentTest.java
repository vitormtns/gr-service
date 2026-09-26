package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.AnimalEventDetails.HealthTreatmentRetractionEventDetails;
import com.gerenciadorrural.modules.herd.domain.AnimalEventRepository;
import com.gerenciadorrural.modules.herd.domain.AnimalEventType;
import com.gerenciadorrural.modules.herd.domain.HealthTreatmentType;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalProfileRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.HealthTreatmentRetraction;
import com.gerenciadorrural.modules.herd.domain.HerdManagementRepository.Treatment;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetractHealthTreatmentTest {
  private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
  private final TenantTransactionExecutor tx = mock();
  private final HerdManagementRepository herd = mock();
  private final HerdAnimalProfileRepository animals = mock();
  private final AnimalEventRepository events = mock();
  private final TenantContext context =
      new TenantContext(
          new TenantId(UUID.randomUUID()),
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID(),
          "OPERATOR",
          "ALL_FARMS");
  private RetractHealthTreatment service;

  @BeforeEach
  void setUp() {
    doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute())
        .when(tx)
        .execute(any(), org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
    service = new RetractHealthTreatment(tx, herd, animals, events, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void persistsNormalizedRetractionAndAuditEventForScopedTarget() {
    UUID animalId = UUID.randomUUID();
    UUID treatmentId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    Treatment treatment = treatment(treatmentId);
    HerdAnimalSummary animal = animal(animalId, 7);
    HealthTreatmentRetraction created =
        new HealthTreatmentRetraction(
            UUID.randomUUID(), operationId, animalId, treatmentId, "motivo", context.userId(), NOW);
    when(herd.findHealthTreatmentRetractionByOperation(
            context.tenantId(), context.farmId(), operationId))
        .thenReturn(Optional.empty());
    when(animals.findById(context.tenantId(), context.farmId(), animalId))
        .thenReturn(Optional.of(animal));
    when(herd.findTreatment(context.tenantId(), context.farmId(), animalId, treatmentId))
        .thenReturn(Optional.of(treatment));
    when(herd.findHealthTreatmentRetractionByTreatment(
            context.tenantId(), context.farmId(), animalId, treatmentId))
        .thenReturn(Optional.empty());
    when(herd.insertHealthTreatmentRetraction(
            context.tenantId(),
            context.farmId(),
            animalId,
            treatmentId,
            operationId,
            "motivo",
            context.userId()))
        .thenReturn(created);

    assertThat(service.retract(context, animalId, treatmentId, operationId, "  motivo  "))
        .isSameAs(created);

    verify(events).lockOperation(context.tenantId(), context.farmId(), operationId);
    verify(events)
        .record(
            context.tenantId(),
            context.farmId(),
            animalId,
            AnimalEventType.HEALTH_TREATMENT_RETRACTED,
            operationId,
            context.userId(),
            LocalDate.of(2026, 9, 22),
            7,
            new HealthTreatmentRetractionEventDetails(treatmentId, created.id(), "motivo"));
  }

  @Test
  void exactReplayReturnsSameRetractionWithoutTargetLookupOrSecondEvent() {
    UUID animalId = UUID.randomUUID();
    UUID treatmentId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    HealthTreatmentRetraction existing =
        new HealthTreatmentRetraction(
            UUID.randomUUID(), operationId, animalId, treatmentId, null, UUID.randomUUID(), NOW);
    when(herd.findHealthTreatmentRetractionByOperation(
            context.tenantId(), context.farmId(), operationId))
        .thenReturn(Optional.of(existing));

    assertThat(service.retract(context, animalId, treatmentId, operationId, "   "))
        .isSameAs(existing);

    verify(events).lockOperation(context.tenantId(), context.farmId(), operationId);
    verify(herd, never()).findTreatment(any(), any(), any(), any());
    verify(events, never()).record(any(), any(), any(), any(), any(), any(), any(), any(Long.class), any());
  }

  @Test
  void sameOperationWithDifferentTreatmentOrReasonConflicts() {
    UUID animalId = UUID.randomUUID();
    UUID treatmentId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    HealthTreatmentRetraction existing =
        new HealthTreatmentRetraction(
            UUID.randomUUID(), operationId, animalId, treatmentId, "original", UUID.randomUUID(), NOW);
    when(herd.findHealthTreatmentRetractionByOperation(
            context.tenantId(), context.farmId(), operationId))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () -> service.retract(context, animalId, treatmentId, operationId, "diferente"))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);
    assertThatThrownBy(
            () -> service.retract(context, animalId, UUID.randomUUID(), operationId, "original"))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);

    verify(events, never()).record(any(), any(), any(), any(), any(), any(), any(), any(Long.class), any());
  }

  @Test
  void newOperationForRetractedTargetConflicts() {
    UUID animalId = UUID.randomUUID();
    UUID treatmentId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    when(herd.findHealthTreatmentRetractionByOperation(
            context.tenantId(), context.farmId(), operationId))
        .thenReturn(Optional.empty());
    when(animals.findById(context.tenantId(), context.farmId(), animalId))
        .thenReturn(Optional.of(animal(animalId, 3)));
    when(herd.findTreatment(context.tenantId(), context.farmId(), animalId, treatmentId))
        .thenReturn(Optional.of(treatment(treatmentId)));
    when(herd.findHealthTreatmentRetractionByTreatment(
            context.tenantId(), context.farmId(), animalId, treatmentId))
        .thenReturn(Optional.of(
            new HealthTreatmentRetraction(
                UUID.randomUUID(), UUID.randomUUID(), animalId, treatmentId, null, null, NOW)));

    assertThatThrownBy(() -> service.retract(context, animalId, treatmentId, operationId, null))
        .isInstanceOf(HerdLifecycleConflictException.class);

    verify(herd, never())
        .insertHealthTreatmentRetraction(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void unknownOrCrossScopeTreatmentIsNotFoundThroughScopedLookup() {
    UUID animalId = UUID.randomUUID();
    UUID treatmentId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    when(herd.findHealthTreatmentRetractionByOperation(
            context.tenantId(), context.farmId(), operationId))
        .thenReturn(Optional.empty());
    when(animals.findById(context.tenantId(), context.farmId(), animalId))
        .thenReturn(Optional.of(animal(animalId, 1)));
    when(herd.findTreatment(context.tenantId(), context.farmId(), animalId, treatmentId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.retract(context, animalId, treatmentId, operationId, null))
        .isInstanceOf(HerdAnimalNotFoundException.class);

    verify(animals).findById(context.tenantId(), context.farmId(), animalId);
    verify(herd).findTreatment(context.tenantId(), context.farmId(), animalId, treatmentId);
  }

  private Treatment treatment(UUID id) {
    return new Treatment(
        id,
        UUID.randomUUID(),
        HealthTreatmentType.VACCINATION,
        null,
        LocalDate.of(2026, 8, 1),
        null,
        null,
        null,
        null,
        context.userId(),
        NOW);
  }

  private HerdAnimalSummary animal(UUID id, long version) {
    return new HerdAnimalSummary(
        id,
        "B-1",
        "Brisa",
        HerdAnimalSex.FEMALE,
        LocalDate.of(2025, 1, 1),
        HerdAnimalStatus.ACTIVE,
        version);
  }
}
