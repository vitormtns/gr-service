package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteConflictException;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteRepository;
import com.gerenciadorrural.modules.herd.domain.NewHerdAnimal;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateCurrentFarmAnimalTest {

    private final TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
    private final HerdAnimalWriteRepository repository = mock(HerdAnimalWriteRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);
    private TenantContext context;
    private CreateCurrentFarmAnimal useCase;

    @BeforeEach
    void setUp() {
        context = context("OWNER");
        useCase = new CreateCurrentFarmAnimal(transactions, repository, clock);
        doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute())
                .when(transactions).execute(any(), any(TenantTransactionalOperation.class));
    }

    @Test
    void permitsEveryApprovedRoleAndRejectsViewerBeforeAnyPersistence() {
        for (String role : new String[]{"OWNER", "ADMIN", "MANAGER", "OPERATOR"}) {
            TenantContext allowed = context(role);
            UUID id = id();
            when(repository.findById(allowed.tenantId(), allowed.farmId(), id)).thenReturn(Optional.empty());
            doAnswer(invocation -> summary((NewHerdAnimal) invocation.getArgument(0))).when(repository).insert(any());
            assertThat(useCase.execute(allowed, command(id, "A-001", "Nome", HerdAnimalSex.FEMALE, null)).outcome())
                    .isEqualTo(CreateCurrentFarmAnimalResult.Outcome.CREATED);
        }
        TenantTransactionExecutor forbiddenTransactions = mock(TenantTransactionExecutor.class);
        HerdAnimalWriteRepository forbiddenRepository = mock(HerdAnimalWriteRepository.class);
        CreateCurrentFarmAnimal forbidden = new CreateCurrentFarmAnimal(forbiddenTransactions, forbiddenRepository, clock);
        assertThatThrownBy(() -> forbidden.execute(context("VIEWER"), command(id(), "A", null, HerdAnimalSex.MALE, null)))
                .isInstanceOf(HerdAnimalCreationForbiddenException.class);
        verify(forbiddenTransactions, never()).execute(any(), any(TenantTransactionalOperation.class));
        verify(forbiddenRepository, never()).findById(any(), any(), any());
    }

    @Test
    void normalizesPosixEdgesPreservesInternalWhitespaceAndUsesOnlyContextIds() {
        UUID id = id();
        AtomicBoolean insideTransaction = new AtomicBoolean();
        doAnswer(invocation -> { insideTransaction.set(true); return ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute(); })
                .when(transactions).execute(eq(context), any(TenantTransactionalOperation.class));
        when(repository.findById(context.tenantId(), context.farmId(), id)).thenReturn(Optional.empty());
        when(repository.insert(any())).thenAnswer(invocation -> {
            assertThat(insideTransaction).isTrue();
            return summary((NewHerdAnimal) invocation.getArgument(0));
        });

        useCase.execute(context, command(id, " \tA  B\n\r\f\u000B", "\t Nome  interno \n", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1)));

        org.mockito.ArgumentCaptor<NewHerdAnimal> animal = org.mockito.ArgumentCaptor.forClass(NewHerdAnimal.class);
        verify(repository).insert(animal.capture());
        assertThat(animal.getValue().id()).isEqualTo(id);
        assertThat(animal.getValue().tenantId()).isEqualTo(context.tenantId());
        assertThat(animal.getValue().farmId()).isEqualTo(context.farmId());
        assertThat(animal.getValue().identification()).isEqualTo("A  B");
        assertThat(animal.getValue().name()).isEqualTo("Nome  interno");
    }

    @Test
    void rejectsInvalidCommandsBeforeOpeningTransactionOrCallingRepository() {
        assertInvalid(new CreateCurrentFarmAnimalCommand(null, "A", null, HerdAnimalSex.MALE, null));
        assertInvalid(new CreateCurrentFarmAnimalCommand(id(), null, null, HerdAnimalSex.MALE, null));
        assertInvalid(command(id(), " \t\n\r\f\u000B", null, HerdAnimalSex.MALE, null));
        assertInvalid(command(id(), "A".repeat(101), null, HerdAnimalSex.MALE, null));
        assertInvalid(command(id(), "A", " \t", HerdAnimalSex.MALE, null));
        assertInvalid(command(id(), "A", "N".repeat(256), HerdAnimalSex.MALE, null));
        assertInvalid(command(id(), "A", null, null, null));
        assertInvalid(command(id(), "A", null, HerdAnimalSex.MALE, LocalDate.of(2026, 9, 6)));
        verify(transactions, never()).execute(any(), any(TenantTransactionalOperation.class));
        verify(repository, never()).findById(any(), any(), any());
    }

    @Test
    void acceptsTodayPastAndNullBirthDateAndPreservesNullName() {
        for (LocalDate date : new LocalDate[]{LocalDate.of(2026, 9, 5), LocalDate.of(1900, 1, 1), null}) {
            UUID id = id();
            when(repository.findById(context.tenantId(), context.farmId(), id)).thenReturn(Optional.empty());
            doAnswer(invocation -> summary((NewHerdAnimal) invocation.getArgument(0))).when(repository).insert(any());
            CreateCurrentFarmAnimalResult result = useCase.execute(context, command(id, "A-" + id, null, HerdAnimalSex.MALE, date));
            assertThat(result.animal().birthDate()).isEqualTo(date);
            assertThat(result.animal().name()).isNull();
        }
    }

    @Test
    void replaysExactNormalizedPayloadWithoutWriting() {
        UUID id = id();
        HerdAnimalSummary existing = new HerdAnimalSummary(id, "A-001", "Nome", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), HerdAnimalStatus.ACTIVE, 4);
        when(repository.findById(context.tenantId(), context.farmId(), id)).thenReturn(Optional.of(existing));
        CreateCurrentFarmAnimalResult result = useCase.execute(context, command(id, " \tA-001\n", " Nome ", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1)));
        assertThat(result.outcome()).isEqualTo(CreateCurrentFarmAnimalResult.Outcome.REPLAYED);
        assertThat(result.animal()).isSameAs(existing);
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsEachDivergentReplayWithoutWriting() {
        UUID id = id();
        HerdAnimalSummary existing = new HerdAnimalSummary(id, "A-001", "Nome", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1), HerdAnimalStatus.ACTIVE, 0);
        for (CreateCurrentFarmAnimalCommand divergent : new CreateCurrentFarmAnimalCommand[]{
                command(id, "Outro", "Nome", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1)),
                command(id, "A-001", "Outro", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 1)),
                command(id, "A-001", "Nome", HerdAnimalSex.MALE, LocalDate.of(2020, 1, 1)),
                command(id, "A-001", "Nome", HerdAnimalSex.FEMALE, LocalDate.of(2020, 1, 2))
        }) {
            when(repository.findById(context.tenantId(), context.farmId(), id)).thenReturn(Optional.of(existing));
            assertThatThrownBy(() -> useCase.execute(context, divergent)).isInstanceOf(HerdAnimalIdempotencyConflictException.class);
        }
        verify(repository, never()).insert(any());
    }

    @Test
    void translatesIdentificationConflictAndKeepsTechnicalFailuresTechnical() {
        UUID id = id();
        when(repository.findById(context.tenantId(), context.farmId(), id)).thenReturn(Optional.empty());
        doThrow(new HerdAnimalWriteConflictException(HerdAnimalWriteConflictException.Type.IDENTIFICATION_CONFLICT, new RuntimeException()))
                .when(repository).insert(any());
        assertThatThrownBy(() -> useCase.execute(context, command(id, "A", null, HerdAnimalSex.MALE, null)))
                .isInstanceOf(HerdAnimalIdentificationConflictException.class);

        UUID other = id();
        RuntimeException technical = new RuntimeException("falha técnica");
        when(repository.findById(context.tenantId(), context.farmId(), other)).thenReturn(Optional.empty());
        doThrow(technical).when(repository).insert(any());
        assertThatThrownBy(() -> useCase.execute(context, command(other, "B", null, HerdAnimalSex.MALE, null))).isSameAs(technical);
    }

    @Test
    void handlesIdConflictRaceByRereadingOnlyTheCurrentContext() {
        UUID id = id();
        HerdAnimalSummary equal = new HerdAnimalSummary(id, "A", null, HerdAnimalSex.MALE, null, HerdAnimalStatus.ACTIVE, 0);
        when(repository.findById(context.tenantId(), context.farmId(), id)).thenReturn(Optional.empty(), Optional.of(equal));
        doThrow(new HerdAnimalWriteConflictException(HerdAnimalWriteConflictException.Type.ID_CONFLICT, new RuntimeException()))
                .when(repository).insert(any());
        assertThat(useCase.execute(context, command(id, "A", null, HerdAnimalSex.MALE, null)).outcome())
                .isEqualTo(CreateCurrentFarmAnimalResult.Outcome.REPLAYED);

        UUID divergentId = id();
        HerdAnimalSummary divergent = new HerdAnimalSummary(divergentId, "Outro", null, HerdAnimalSex.MALE, null, HerdAnimalStatus.ACTIVE, 0);
        when(repository.findById(context.tenantId(), context.farmId(), divergentId)).thenReturn(Optional.empty(), Optional.of(divergent));
        doThrow(new HerdAnimalWriteConflictException(HerdAnimalWriteConflictException.Type.ID_CONFLICT, new RuntimeException()))
                .when(repository).insert(any());
        assertThatThrownBy(() -> useCase.execute(context, command(divergentId, "A", null, HerdAnimalSex.MALE, null)))
                .isInstanceOf(HerdAnimalIdempotencyConflictException.class);

        UUID invisibleId = id();
        when(repository.findById(context.tenantId(), context.farmId(), invisibleId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(context, command(invisibleId, "C", null, HerdAnimalSex.MALE, null)))
                .isInstanceOf(HerdAnimalIdempotencyConflictException.class);
    }

    private void assertInvalid(CreateCurrentFarmAnimalCommand command) {
        assertThatThrownBy(() -> useCase.execute(context, command)).isInstanceOf(HerdAnimalCommandInvalidException.class);
    }

    private static CreateCurrentFarmAnimalCommand command(UUID id, String identification, String name, HerdAnimalSex sex, LocalDate birthDate) {
        return new CreateCurrentFarmAnimalCommand(id, identification, name, sex, birthDate);
    }

    private static HerdAnimalSummary summary(NewHerdAnimal animal) {
        return new HerdAnimalSummary(animal.id(), animal.identification(), animal.name(), animal.sex(), animal.birthDate(), HerdAnimalStatus.ACTIVE, 0);
    }

    private static UUID id() { return UUID.randomUUID(); }

    private static TenantContext context(String role) {
        return new TenantContext(new TenantId(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), role, "ALL_FARMS");
    }
}
