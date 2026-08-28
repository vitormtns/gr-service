package com.gerenciadorrural.shared.tenancy;

public class TenantTransactionContextConflictException extends RuntimeException {

    public TenantTransactionContextConflictException() {
        super("A transação já possui outro contexto de tenant");
    }
}
