package com.gerenciadorrural.shared.tenancy;

public interface TenantTransactionExecutor {

    <T> T execute(TenantContext context, TenantTransactionalOperation<T> operation);

    void execute(TenantContext context, TenantTransactionalAction action);
}
