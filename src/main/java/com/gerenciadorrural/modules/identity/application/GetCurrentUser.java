package com.gerenciadorrural.modules.identity.application;

import com.gerenciadorrural.shared.security.application.CurrentUserProvider;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class GetCurrentUser {

    private final CurrentUserProvider currentUserProvider;
    private final SynchronizeAuthenticatedUser synchronizeAuthenticatedUser;

    public GetCurrentUser(
            CurrentUserProvider currentUserProvider,
            SynchronizeAuthenticatedUser synchronizeAuthenticatedUser
    ) {
        this.currentUserProvider = currentUserProvider;
        this.synchronizeAuthenticatedUser = synchronizeAuthenticatedUser;
    }

    public SynchronizedCurrentUser execute() {
        AuthenticatedUser authenticatedUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new IllegalStateException("A identidade autenticada não está disponível"));
        return new SynchronizedCurrentUser(
                synchronizeAuthenticatedUser.execute(authenticatedUser),
                authenticatedUser
        );
    }
}
