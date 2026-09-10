package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalProfileRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class GetCurrentFarmAnimal {
    private final TenantTransactionExecutor transactions;
    private final HerdAnimalProfileRepository repository;

    public GetCurrentFarmAnimal(TenantTransactionExecutor transactions, HerdAnimalProfileRepository repository) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
    }

    public HerdAnimalSummary execute(TenantContext context, UUID id) {
        Objects.requireNonNull(context, "O contexto de tenant é obrigatório");
        Objects.requireNonNull(id, "O ID do animal é obrigatório");
        return transactions.execute(context, () -> repository.findById(context.tenantId(), context.farmId(), id)
                .orElseThrow(HerdAnimalNotFoundException::new));
    }
}
