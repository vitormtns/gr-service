package com.gerenciadorrural.modules.farms.application;

import com.gerenciadorrural.modules.farms.domain.FarmProfile;
import com.gerenciadorrural.modules.farms.domain.FarmProfileQueryRepository;
import com.gerenciadorrural.modules.farms.domain.FarmProfileUpdateResult;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionInfrastructureException;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateCurrentFarmProfileTest {

    private final TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
    private final FarmProfileQueryRepository repository = mock(FarmProfileQueryRepository.class);
    private final TenantContext context = context();
    private final FarmProfile profile = new FarmProfile(
            context.farmId(), context.tenantId(), "Fazenda São João", "ACTIVE", 1
    );
    private UpdateCurrentFarmProfile useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateCurrentFarmProfile(transactions, repository);
    }

    @Test
    void rejectsInvalidInputBeforeOpeningTransactionOrCallingRepository() {
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.execute(null, "Nome", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.execute(context, null, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.execute(context, "", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.execute(context, "   ", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.execute(context, "x".repeat(256), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.execute(context, "Nome", -1));

        verify(transactions, never()).execute(any(), any(TenantTransactionalOperation.class));
        verify(repository, never()).updateName(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void normalizesOnlyExternalWhitespaceAndPassesContextAndVersionInsideTransaction() {
        AtomicBoolean callbackRunning = new AtomicBoolean(false);
        when(transactions.execute(eq(context), any(TenantTransactionalOperation.class))).thenAnswer(invocation -> {
            callbackRunning.set(true);
            return ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute();
        });
        when(repository.updateName(context.tenantId(), context.farmId(), "Fazenda  São João", 0))
                .thenAnswer(invocation -> {
                    assertThat(callbackRunning).isTrue();
                    return new FarmProfileUpdateResult.Updated(profile);
                });

        assertThat(useCase.execute(context, "  Fazenda  São João  ", 0)).isSameAs(profile);

        verify(transactions).execute(eq(context), any(TenantTransactionalOperation.class));
        verify(repository).updateName(context.tenantId(), context.farmId(), "Fazenda  São João", 0);
    }

    @Test
    void acceptsExactly255CharactersAndPreservesAccentsAndCase() {
        String name = "Á" + "a".repeat(254);
        executeRepositoryCallback();
        when(repository.updateName(context.tenantId(), context.farmId(), name, 0))
                .thenReturn(new FarmProfileUpdateResult.Updated(profile));

        assertThat(useCase.execute(context, name, 0)).isSameAs(profile);
        verify(repository).updateName(context.tenantId(), context.farmId(), name, 0);
    }

    @Test
    void mapsExplicitFunctionalResults() {
        executeRepositoryCallback();
        when(repository.updateName(context.tenantId(), context.farmId(), "Nome", 0))
                .thenReturn(new FarmProfileUpdateResult.VersionConflict());
        assertThatThrownBy(() -> useCase.execute(context, "Nome", 0))
                .isInstanceOf(FarmProfileVersionConflictException.class);

        when(repository.updateName(context.tenantId(), context.farmId(), "Outro", 0))
                .thenReturn(new FarmProfileUpdateResult.NotAvailable());
        assertThatThrownBy(() -> useCase.execute(context, "Outro", 0))
                .isInstanceOf(FarmProfileNotAvailableException.class);
    }

    @Test
    void preservesTechnicalFailuresWithoutTurningThemIntoFunctionalResults() {
        executeRepositoryCallback();
        DataRetrievalFailureException databaseFailure = new DataRetrievalFailureException("falha técnica");
        when(repository.updateName(context.tenantId(), context.farmId(), "Nome", 0)).thenThrow(databaseFailure);
        assertThatThrownBy(() -> useCase.execute(context, "Nome", 0)).isSameAs(databaseFailure);

        IllegalStateException unexpectedFailure = new IllegalStateException("falha inesperada");
        when(repository.updateName(context.tenantId(), context.farmId(), "Outro", 0)).thenThrow(unexpectedFailure);
        assertThatThrownBy(() -> useCase.execute(context, "Outro", 0)).isSameAs(unexpectedFailure);

        TenantTransactionInfrastructureException transactionFailure = new TenantTransactionInfrastructureException();
        when(transactions.execute(eq(context), any(TenantTransactionalOperation.class))).thenThrow(transactionFailure);
        assertThatThrownBy(() -> useCase.execute(context, "Mais um", 0)).isSameAs(transactionFailure);
    }

    private void executeRepositoryCallback() {
        doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute())
                .when(transactions).execute(eq(context), any(TenantTransactionalOperation.class));
    }

    private static TenantContext context() {
        return new TenantContext(new TenantId(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "OWNER", "ALL_FARMS");
    }
}
