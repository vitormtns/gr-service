package com.gerenciadorrural.modules.identity.application;

public class InternalUserConflictException extends RuntimeException {

    public InternalUserConflictException() {
        super("Não foi possível sincronizar a identidade devido a alterações concorrentes");
    }
}
