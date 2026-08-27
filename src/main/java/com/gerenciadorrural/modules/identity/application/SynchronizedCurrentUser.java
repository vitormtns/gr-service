package com.gerenciadorrural.modules.identity.application;

import com.gerenciadorrural.modules.identity.domain.InternalUser;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;

import java.util.Objects;

public record SynchronizedCurrentUser(
        InternalUser internalUser,
        AuthenticatedUser authentication
) {

    public SynchronizedCurrentUser {
        Objects.requireNonNull(internalUser, "internalUser é obrigatório");
        Objects.requireNonNull(authentication, "authentication é obrigatório");
    }
}
