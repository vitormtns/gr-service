package com.gerenciadorrural.modules.identity.application;

public class InternalUserSuspendedException extends RuntimeException {

    public InternalUserSuspendedException() {
        super("O usuário interno está suspenso");
    }
}
