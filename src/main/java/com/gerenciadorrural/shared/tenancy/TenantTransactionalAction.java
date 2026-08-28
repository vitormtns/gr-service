package com.gerenciadorrural.shared.tenancy;

@FunctionalInterface
public interface TenantTransactionalAction {

    void execute();
}
