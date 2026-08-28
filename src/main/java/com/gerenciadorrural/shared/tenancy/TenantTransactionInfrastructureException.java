package com.gerenciadorrural.shared.tenancy;

public class TenantTransactionInfrastructureException extends RuntimeException {

    private static final String MESSAGE = "Não foi possível preparar o contexto transacional do tenant";

    public TenantTransactionInfrastructureException() {
        super(MESSAGE);
    }

    public TenantTransactionInfrastructureException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
