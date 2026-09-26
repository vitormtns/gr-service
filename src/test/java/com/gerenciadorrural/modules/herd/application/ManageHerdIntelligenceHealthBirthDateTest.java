package com.gerenciadorrural.modules.herd.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManageHerdIntelligenceHealthBirthDateTest {

    TenantTransactionExecutor tx = mock();
    HerdAnimalProfileRepository animals = mock();
    HerdManagementRepository repo = mock();
    AnimalEventRepository events = mock();

    TenantId tenant = new TenantId(UUID.randomUUID());
    UUID user = UUID.randomUUID();
    UUID farm = UUID.randomUUID();
    TenantContext c = new TenantContext(tenant, user, farm, UUID.randomUUID(), "OPERATOR", "ALL_FARMS");
    ManageHerdIntelligence service;

    Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        doAnswer(x -> ((TenantTransactionalOperation<?>) x.getArgument(1)).execute())
                .when(tx).execute(any(), org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
        service = new ManageHerdIntelligence(tx, animals, repo, events,
                new ObjectMapper().findAndRegisterModules(), clock);
        when(repo.operation(eq(tenant), eq(farm), any())).thenReturn(Optional.empty());
    }

    private void stubAnimal(UUID id, LocalDate birthDate, long version) {
        HerdAnimalSummary current = new HerdAnimalSummary(id, "A-" + id.toString().substring(0, 4), null,
                HerdAnimalSex.FEMALE, birthDate, HerdAnimalStatus.ACTIVE, version);
        HerdAnimalSummary touched = new HerdAnimalSummary(id, "A-" + id.toString().substring(0, 4), null,
                HerdAnimalSex.FEMALE, birthDate, HerdAnimalStatus.ACTIVE, version + 1);
        when(animals.findByIdForCorrection(eq(tenant), eq(farm), eq(id)))
                .thenReturn(Optional.of(current));
        when(animals.touch(eq(tenant), eq(farm), eq(id), eq(version)))
                .thenReturn(Optional.of(touched));
    }

    private List<ManageHerdIntelligence.BasicAnimalCommand> cmd(UUID id, long version) {
        return List.of(new ManageHerdIntelligence.BasicAnimalCommand(id, version));
    }

    @Test
    void rejectsTreatmentDayBeforeBirth() {
        UUID id = UUID.randomUUID();
        stubAnimal(id, LocalDate.of(2026, 1, 15), 0);

        assertThatThrownBy(() -> service.health(
                c, UUID.randomUUID(), HealthTreatmentType.VACCINATION, null,
                LocalDate.of(2026, 1, 14), null, null, null, null, cmd(id, 0)))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);

        verify(repo, never()).treatment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(repo, never()).saveOperation(any(), any(), any(), any(), any());
    }

    @Test
    void acceptsTreatmentDayEqualToBirth() {
        UUID id = UUID.randomUUID();
        stubAnimal(id, LocalDate.of(2026, 1, 15), 0);

        ManageHerdIntelligence.Result result = service.health(
                c, UUID.randomUUID(), HealthTreatmentType.VACCINATION, null,
                LocalDate.of(2026, 1, 15), null, null, null, null, cmd(id, 0));

        assertThat(result.replayed()).isFalse();
        verify(repo).treatment(eq(tenant), eq(farm), eq(id), any(),
                eq(HealthTreatmentType.VACCINATION), isNull(),
                eq(LocalDate.of(2026, 1, 15)), isNull(), isNull(), isNull(), isNull(), eq(user));
    }

    @Test
    void acceptsTreatmentDayAfterBirth() {
        UUID id = UUID.randomUUID();
        stubAnimal(id, LocalDate.of(2026, 1, 15), 0);

        ManageHerdIntelligence.Result result = service.health(
                c, UUID.randomUUID(), HealthTreatmentType.VACCINATION, null,
                LocalDate.of(2026, 1, 16), null, null, null, null, cmd(id, 0));

        assertThat(result.replayed()).isFalse();
        verify(repo).treatment(eq(tenant), eq(farm), eq(id), any(),
                eq(HealthTreatmentType.VACCINATION), isNull(),
                eq(LocalDate.of(2026, 1, 16)), isNull(), isNull(), isNull(), isNull(), eq(user));
    }

    @Test
    void rejectsBrucellosisVaccinationBeforeBirthByGenericRule() {
        UUID id = UUID.randomUUID();
        stubAnimal(id, LocalDate.of(2026, 1, 15), 0);

        assertThatThrownBy(() -> service.health(
                c, UUID.randomUUID(), HealthTreatmentType.VACCINATION, HealthProcedureCode.BRUCELLOSIS,
                LocalDate.of(2026, 1, 14), null, null, null, null, cmd(id, 0)))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);

        verify(repo, never()).treatment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDewormingBeforeBirth() {
        UUID id = UUID.randomUUID();
        stubAnimal(id, LocalDate.of(2026, 1, 15), 0);

        assertThatThrownBy(() -> service.health(
                c, UUID.randomUUID(), HealthTreatmentType.DEWORMING, null,
                LocalDate.of(2026, 1, 14), null, null, null, null, cmd(id, 0)))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);

        verify(repo, never()).treatment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsBatchWhenAnyAnimalIsBornAfterTreatmentDay() {
        UUID animalA = UUID.randomUUID();
        UUID animalB = UUID.randomUUID();
        stubAnimal(animalA, LocalDate.of(2026, 1, 1), 0);
        stubAnimal(animalB, LocalDate.of(2026, 6, 1), 0);

        List<ManageHerdIntelligence.BasicAnimalCommand> batch = List.of(
                new ManageHerdIntelligence.BasicAnimalCommand(animalA, 0),
                new ManageHerdIntelligence.BasicAnimalCommand(animalB, 0));

        assertThatThrownBy(() -> service.health(
                c, UUID.randomUUID(), HealthTreatmentType.VACCINATION, null,
                LocalDate.of(2026, 3, 1), null, null, null, null, batch))
                .isInstanceOf(HerdAnimalCommandInvalidException.class);

        verify(repo, never()).treatment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(repo, never()).saveOperation(any(), any(), any(), any(), any());
    }

    @Test
    void acceptsBatchWhenAllAnimalsBornOnOrBeforeTreatmentDay() {
        UUID animalA = UUID.randomUUID();
        UUID animalB = UUID.randomUUID();
        stubAnimal(animalA, LocalDate.of(2026, 1, 1), 0);
        stubAnimal(animalB, LocalDate.of(2026, 3, 1), 0);

        List<ManageHerdIntelligence.BasicAnimalCommand> batch = List.of(
                new ManageHerdIntelligence.BasicAnimalCommand(animalA, 0),
                new ManageHerdIntelligence.BasicAnimalCommand(animalB, 0));

        ManageHerdIntelligence.Result result = service.health(
                c, UUID.randomUUID(), HealthTreatmentType.VACCINATION, null,
                LocalDate.of(2026, 3, 1), null, null, null, null, batch);

        assertThat(result.replayed()).isFalse();
        verify(repo, times(2)).treatment(eq(tenant), eq(farm), any(), any(),
                eq(HealthTreatmentType.VACCINATION), isNull(),
                eq(LocalDate.of(2026, 3, 1)), isNull(), isNull(), isNull(), isNull(), eq(user));
    }
}
