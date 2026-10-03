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

    private final java.time.Clock clock;

    public GetCurrentFarmAnimal(TenantTransactionExecutor transactions, HerdAnimalProfileRepository repository) {
        this(transactions, repository, java.time.Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GetCurrentFarmAnimal(TenantTransactionExecutor transactions, HerdAnimalProfileRepository repository, java.time.Clock clock) {
        this.clock = clock;
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
    }

    public HerdAnimalSummary execute(TenantContext context, UUID id) {
        Objects.requireNonNull(context, "O contexto de tenant é obrigatório");
        Objects.requireNonNull(id, "O ID do animal é obrigatório");
        java.time.LocalDate reference = java.time.LocalDate.now(clock);
        return transactions.execute(context, () -> repository.findById(context.tenantId(), context.farmId(), id)
                .orElseThrow(HerdAnimalNotFoundException::new).withAgeAt(reference));
    }
}
