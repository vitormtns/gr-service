package com.gerenciadorrural.shared.tenancy;

@FunctionalInterface
public interface TenantTransactionalOperation<T> {

    T execute();
}
