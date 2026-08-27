package com.gerenciadorrural.shared.security.application;

import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class GetCurrentUser {

    private final CurrentUserProvider currentUserProvider;

    public GetCurrentUser(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    public AuthenticatedUser execute() {
        return currentUserProvider.currentUser()
                .orElseThrow(() -> new IllegalStateException("A identidade autenticada não está disponível"));
    }
}
