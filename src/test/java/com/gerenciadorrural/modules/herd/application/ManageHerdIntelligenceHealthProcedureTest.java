package com.gerenciadorrural.modules.herd.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManageHerdIntelligenceHealthProcedureTest {

    TenantTransactionExecutor tx = mock();
    HerdAnimalProfileRepository animals = mock();
    HerdManagementRepository repo = mock();
    AnimalEventRepository events = mock();

    TenantId tenant = new TenantId(UUID.randomUUID());
    UUID user = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    TenantContext c = new TenantContext(tenant, user, farm, UUID.randomUUID(), "OPERATOR", "ALL_FARMS");
    ManageHerdIntelligence service;

    UUID animal = UUID.randomUUID();
    LocalDate day = LocalDate.now();

    @BeforeEach
    void setup() {
        doAnswer(x -> ((TenantTransactionalOperation<?>) x.getArgument(1)).execute())
                .when(tx).execute(any(), org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
        service = new ManageHerdIntelligence(tx, animals, repo, events,
                new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
        HerdAnimalSummary current = new HerdAnimalSummary(animal, "A1", null,
                HerdAnimalSex.FEMALE, LocalDate.of(2025, 1, 1), HerdAnimalStatus.ACTIVE, 0);
        HerdAnimalSummary touched = new HerdAnimalSummary(animal, "A1", null,
                HerdAnimalSex.FEMALE, LocalDate.of(2025, 1, 1), HerdAnimalStatus.ACTIVE, 1);
        when(animals.findByIdForCorrection(eq(tenant), eq(farm), eq(animal)))
                .thenReturn(Optional.of(current));
        when(animals.touch(eq(tenant), eq(farm), eq(animal), eq(0L)))
                .thenReturn(Optional.of(touched));
        when(repo.operation(eq(tenant), eq(farm), any())).thenReturn(Optional.empty());
    }

    private List<ManageHerdIntelligence.BasicAnimalCommand> cmd() {
        return List.of(new ManageHerdIntelligence.BasicAnimalCommand(animal, 0));
    }

    private String savedCanonical() {
        ArgumentCaptor<String> canonical = ArgumentCaptor.forClass(String.class);
        verify(repo).saveOperation(eq(tenant), eq(farm), any(), eq("HEALTH"), canonical.capture());
        return canonical.getValue();
    }

    @Test
    void acceptsNullProcedureCodeWithoutChangingCanonicalShape() {
        ManageHerdIntelligence.Result vaccination = service.health(
                c, UUID.randomUUID(), HealthTreatmentType.VACCINATION, null, day, null, null, null, null, cmd());
        ManageHerdIntelligence.Result deworming = service.health(
                c, UUID.randomUUID(), HealthTreatmentType.DEWORMING, null, day, null, null, null, null, cmd());

        assertThat(vaccination.replayed()).isFalse();
        assertThat(deworming.replayed()).isFalse();

        ArgumentCaptor<String> canonical = ArgumentCaptor.forClass(String.class);
        verify(repo, times(2)).saveOperation(eq(tenant), eq(farm), any(), eq("HEALTH"), canonical.capture());
        assertThat(canonical.getAllValues())
                .allSatisfy(payload -> assertThat(payload).doesNotContain("procedureCode"));
    }

    @Test
    void acceptsVaccinationWithBrucellosisAndPropagatesIdentity() {
        UUID op = UUID.randomUUID();

        ManageHerdIntelligence.Result result = service.health(
                c, op, HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                day, "Vacina X", null, null, "notes", cmd());

        assertThat(result.replayed()).isFalse();

        ArgumentCaptor<HealthProcedureCode> procedure = ArgumentCaptor.forClass(HealthProcedureCode.class);
        verify(repo).treatment(eq(tenant), eq(farm), eq(animal), eq(op), eq(HealthTreatmentType.VACCINATION),
                procedure.capture(), eq(day), eq("Vacina X"), isNull(), isNull(), eq("notes"), eq(user));
        assertThat(procedure.getValue()).isEqualTo(HealthProcedureCode.BRUCELLOSIS);

        ArgumentCaptor<AnimalEventDetails> details = ArgumentCaptor.forClass(AnimalEventDetails.class);
        verify(events).record(eq(tenant), eq(farm), eq(animal), eq(AnimalEventType.HEALTH_TREATMENT),
                eq(op), eq(user), eq(day), eq(1L), details.capture());
        assertThat(details.getValue()).isInstanceOfSatisfying(HealthTreatmentEventDetails.class,
                health -> {
                    assertThat(health.procedureCode()).isEqualTo(HealthProcedureCode.BRUCELLOSIS);
                    assertThat(health.treatmentType()).isEqualTo(HealthTreatmentType.VACCINATION);
                });

        assertThat(savedCanonical()).contains("\"procedureCode\":\"BRUCELLOSIS\"");
    }

    @Test
    void rejectsDewormingWithBrucellosis() {
        assertThatThrownBy(() -> service.health(
                c, UUID.randomUUID(), HealthTreatmentType.DEWORMING, HealthProcedureCode.BRUCELLOSIS,
                day, null, null, null, null, cmd()))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);

        verify(repo, never()).treatment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replaysIdenticalNullProcedureWithoutConflict() {
        UUID op = UUID.randomUUID();
        service.health(c, op, HealthTreatmentType.VACCINATION, null, day, null, null, null, null, cmd());
        String canonical = savedCanonical();
        when(repo.operation(eq(tenant), eq(farm), eq(op))).thenReturn(Optional.of(canonical));

        ManageHerdIntelligence.Result replay = service.health(
                c, op, HealthTreatmentType.VACCINATION, null, day, null, null, null, null, cmd());

        assertThat(replay.replayed()).isTrue();
    }

    @Test
    void replaysIdenticalBrucellosisProcedureWithoutConflict() {
        UUID op = UUID.randomUUID();
        service.health(c, op, HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                day, null, null, null, null, cmd());
        String canonical = savedCanonical();
        when(repo.operation(eq(tenant), eq(farm), eq(op))).thenReturn(Optional.of(canonical));

        ManageHerdIntelligence.Result replay = service.health(
                c, op, HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                day, null, null, null, null, cmd());

        assertThat(replay.replayed()).isTrue();
    }

    @Test
    void conflictsWhenReplayAddsBrucellosis() {
        UUID op = UUID.randomUUID();
        service.health(c, op, HealthTreatmentType.VACCINATION, null, day, null, null, null, null, cmd());
        String canonical = savedCanonical();
        when(repo.operation(eq(tenant), eq(farm), eq(op))).thenReturn(Optional.of(canonical));

        assertThatThrownBy(() -> service.health(
                c, op, HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                day, null, null, null, null, cmd()))
                .isInstanceOf(HerdOperationIdempotencyConflictException.class);
    }

    @Test
    void conflictsWhenReplayRemovesBrucellosis() {
        UUID op = UUID.randomUUID();
        service.health(c, op, HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                day, null, null, null, null, cmd());
        String canonical = savedCanonical();
        when(repo.operation(eq(tenant), eq(farm), eq(op))).thenReturn(Optional.of(canonical));

        assertThatThrownBy(() -> service.health(
                c, op, HealthTreatmentType.VACCINATION, null, day, null, null, null, null, cmd()))
                .isInstanceOf(HerdOperationIdempotencyConflictException.class);
    }
}
