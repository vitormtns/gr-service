package com.gerenciadorrural.modules.farms.application;

import com.gerenciadorrural.modules.farms.domain.FarmProfile;
import com.gerenciadorrural.modules.farms.domain.FarmProfileQueryRepository;
import com.gerenciadorrural.modules.farms.domain.FarmProfileUpdateResult;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import org.springframework.stereotype.Service;

@Service
public class UpdateCurrentFarmProfile {

    private final TenantTransactionExecutor transactions;
    private final FarmProfileQueryRepository repository;

    public UpdateCurrentFarmProfile(TenantTransactionExecutor transactions, FarmProfileQueryRepository repository) {
        this.transactions = transactions;
        this.repository = repository;
    }

    public FarmProfile execute(TenantContext context, String name, long expectedVersion) {
        if (context == null || name == null || expectedVersion < 0) {
            throw new IllegalArgumentException();
        }
        String normalizedName = name.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 255) {
            throw new IllegalArgumentException();
        }
        return transactions.execute(context, () -> mapResult(repository.updateName(
                context.tenantId(), context.farmId(), normalizedName, expectedVersion
        )));
    }

    private FarmProfile mapResult(FarmProfileUpdateResult result) {
        if (result instanceof FarmProfileUpdateResult.Updated updated) {
            return updated.profile();
        }
        if (result instanceof FarmProfileUpdateResult.NotAvailable) {
            throw new FarmProfileNotAvailableException();
        }
        if (result instanceof FarmProfileUpdateResult.VersionConflict) {
            throw new FarmProfileVersionConflictException();
        }
        throw new IllegalStateException("Resultado de atualização de perfil inválido");
    }
}
