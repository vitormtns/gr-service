package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalPage;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalQuery;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalQueryRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalAction;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListCurrentFarmAnimalsTest {

    @Test
    void forwardsTheCompleteQueryAndPreservesTheRepositoryPage() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContext context = context(tenantId);
        HerdAnimalQuery query = new HerdAnimalQuery(
            "Água doce", HerdAnimalSex.FEMALE, HerdAnimalStatus.SOLD, 2, 7
        );
        HerdAnimalSummary animal = new HerdAnimalSummary(
            UUID.randomUUID(), "A-001", "Água", HerdAnimalSex.FEMALE,
            LocalDate.of(2024, 1, 2), HerdAnimalStatus.SOLD, 4
        );
        HerdAnimalPage expected = new HerdAnimalPage(List.of(animal), 2, 7, 15);
        HerdAnimalQueryRepository repository = mock(HerdAnimalQueryRepository.class);
        when(repository.list(tenantId, context.farmId(), query)).thenReturn(expected);

        HerdAnimalPage result = new ListCurrentFarmAnimals(
            synchronousTransactions(context), repository
        ).execute(context, query);

        assertThat(result).isSameAs(expected);
        assertThat(result.items()).containsExactly(animal);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(7);
        assertThat(result.totalElements()).isEqualTo(15);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void keepsAnEmptyPageBeyondTheEndValid() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContext context = context(tenantId);
        HerdAnimalQuery query = new HerdAnimalQuery(null, null, null, 4, 10);
        HerdAnimalPage expected = new HerdAnimalPage(List.of(), 4, 10, 12);
        HerdAnimalQueryRepository repository = mock(HerdAnimalQueryRepository.class);
        when(repository.list(tenantId, context.farmId(), query)).thenReturn(expected);

        HerdAnimalPage result = new ListCurrentFarmAnimals(
            synchronousTransactions(context), repository
        ).execute(context, query);

        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(4);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(12);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void propagatesTechnicalRepositoryFailuresWithoutChangingTheirMessage() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContext context = context(tenantId);
        HerdAnimalQuery query = new HerdAnimalQuery(null, null, null, 0, 50);
        DataAccessResourceFailureException failure =
            new DataAccessResourceFailureException("jdbc:postgresql://internal-host/app");
        HerdAnimalQueryRepository repository = mock(HerdAnimalQueryRepository.class);
        when(repository.list(any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(
            () -> new ListCurrentFarmAnimals(
                synchronousTransactions(context), repository
            ).execute(context, query)
        ).isSameAs(failure).hasMessage("jdbc:postgresql://internal-host/app");
    }

    @Test
    void rejectsNullContextBeforeCallingTheRepository() {
        HerdAnimalQueryRepository repository = mock(HerdAnimalQueryRepository.class);
        ListCurrentFarmAnimals useCase = new ListCurrentFarmAnimals(
            synchronousTransactions(null), repository
        );

        assertThatNullPointerException().isThrownBy(
            () -> useCase.execute(null, new HerdAnimalQuery(null, null, null, 0, 50))
        );
    }

    private static TenantContext context(TenantId tenantId) {
        return new TenantContext(
            tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "OWNER", "ALL_FARMS"
        );
    }

    private static TenantTransactionExecutor synchronousTransactions(
        TenantContext expectedContext
    ) {
        return new TenantTransactionExecutor() {
            @Override
            public <T> T execute(
                TenantContext context,
                TenantTransactionalOperation<T> operation
            ) {
                assertThat(context).isSameAs(expectedContext);
                return operation.execute();
            }

            @Override
            public void execute(TenantContext context, TenantTransactionalAction action) {
                assertThat(context).isSameAs(expectedContext);
                action.execute();
            }
        };
    }
}
