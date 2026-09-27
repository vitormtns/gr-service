package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.domain.MilkRecordRepository.MilkRecord;
import com.gerenciadorrural.shared.tenancy.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MilkServiceTest {
  private final TenantContext context = new TenantContext(new TenantId(UUID.randomUUID()),
      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER", "ALL_FARMS");
  private final UUID animalId = UUID.randomUUID();
  private final HerdAnimalProfileRepository animals = mock(HerdAnimalProfileRepository.class);
  private final AnimalEventRepository events = mock(AnimalEventRepository.class);
  private final MilkRecordRepository records = mock(MilkRecordRepository.class);
  private final TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
  private final MilkService service = new MilkService(transactions, animals, events, records,
      Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));

  @BeforeEach
  void setup() {
    doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute())
        .when(transactions).execute(any(), any(TenantTransactionalOperation.class));
    when(events.findByOperation(any(), any(), any())).thenReturn(Optional.empty());
  }

  @Test
  void recordsFemaleProductionAndReplaysTheSameOperation() {
    UUID operation = UUID.randomUUID();
    MilkService.Command command = new MilkService.Command(operation, 0L,
        LocalDate.of(2026, 9, 26), new BigDecimal("12.500"), MilkSession.MORNING, " Ordenha ");
    HerdAnimalSummary female = animal(HerdAnimalSex.FEMALE, HerdAnimalStatus.ACTIVE, 0);
    when(animals.findByIdForCorrection(context.tenantId(), context.farmId(), animalId))
        .thenReturn(Optional.of(female));
    when(animals.touch(context.tenantId(), context.farmId(), animalId, 0))
        .thenReturn(Optional.of(animal(HerdAnimalSex.FEMALE, HerdAnimalStatus.ACTIVE, 1)));
    MilkRecord saved = new MilkRecord(UUID.randomUUID(), operation, animalId,
        command.recordedOn(), new BigDecimal("12.5"), MilkSession.MORNING,
        "Ordenha", context.userId(), Instant.now());
    when(records.insert(any(), any(), eq(animalId), eq(operation), any(), any(), any(), any(), any()))
        .thenReturn(saved);
    assertThat(service.record(context, animalId, command)).isEqualTo(saved);
    MilkRecordedEventDetails details = new MilkRecordedEventDetails(new BigDecimal("12.5"),
        MilkSession.MORNING, "Ordenha");
    verify(events).record(eq(context.tenantId()), eq(context.farmId()), eq(animalId),
        eq(AnimalEventType.MILK_RECORDED), eq(operation), eq(context.userId()),
        eq(command.recordedOn()), eq(1L), eq(details));

    when(events.findByOperation(context.tenantId(), context.farmId(), operation))
        .thenReturn(Optional.of(new AnimalEvent(UUID.randomUUID(), animalId,
            AnimalEventType.MILK_RECORDED, operation, context.userId(), command.recordedOn(),
            Instant.now(), 1, details)));
    when(records.byOperation(context.tenantId(), context.farmId(), operation))
        .thenReturn(Optional.of(saved));
    assertThat(service.record(context, animalId, command)).isEqualTo(saved);
    assertThatThrownBy(() -> service.record(context, animalId,
        new MilkService.Command(operation, 0L, command.recordedOn(), new BigDecimal("13"),
            MilkSession.MORNING, "Ordenha")))
        .isInstanceOf(HerdOperationIdempotencyConflictException.class);
  }

  @Test
  void rejectsMaleInactiveAndPreBirthRecords() {
    MilkService.Command command = new MilkService.Command(UUID.randomUUID(), 0L,
        LocalDate.of(2026, 9, 26), BigDecimal.ONE, null, null);
    when(animals.findByIdForCorrection(context.tenantId(), context.farmId(), animalId))
        .thenReturn(Optional.of(animal(HerdAnimalSex.MALE, HerdAnimalStatus.ACTIVE, 0)));
    assertThatThrownBy(() -> service.record(context, animalId, command))
        .isInstanceOf(HerdLifecycleConflictException.class);
    when(animals.findByIdForCorrection(context.tenantId(), context.farmId(), animalId))
        .thenReturn(Optional.of(animal(HerdAnimalSex.FEMALE, HerdAnimalStatus.SOLD, 0)));
    assertThatThrownBy(() -> service.record(context, animalId, command))
        .isInstanceOf(HerdLifecycleConflictException.class);
    assertThatThrownBy(() -> service.record(context, animalId,
        new MilkService.Command(UUID.randomUUID(), 0L, LocalDate.of(2026, 9, 28),
            BigDecimal.ONE, null, null)))
        .isInstanceOf(HerdAnimalCommandInvalidException.class);
    verify(records, never()).insert(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  private HerdAnimalSummary animal(HerdAnimalSex sex, HerdAnimalStatus status, long version) {
    return new HerdAnimalSummary(animalId, "A-1", "Brisa", sex,
        LocalDate.of(2024, 1, 1), status, version);
  }
}
