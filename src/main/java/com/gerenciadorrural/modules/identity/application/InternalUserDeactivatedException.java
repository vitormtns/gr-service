package com.gerenciadorrural.modules.identity.application;

public class InternalUserDeactivatedException extends RuntimeException {

    public InternalUserDeactivatedException() {
        super("O usuário interno está desativado");
    }
}
