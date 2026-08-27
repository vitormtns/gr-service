package com.gerenciadorrural.modules.organizations.application;

import com.gerenciadorrural.modules.identity.application.SynchronizeAuthenticatedUser;
import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganization;
import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganizationRepository;
import com.gerenciadorrural.shared.security.application.CurrentUserProvider;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListCurrentUserOrganizations {

    private final CurrentUserProvider currentUserProvider;
    private final SynchronizeAuthenticatedUser synchronizeAuthenticatedUser;
    private final AccessibleOrganizationRepository repository;

    public ListCurrentUserOrganizations(
            CurrentUserProvider currentUserProvider,
            SynchronizeAuthenticatedUser synchronizeAuthenticatedUser,
            AccessibleOrganizationRepository repository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.synchronizeAuthenticatedUser = synchronizeAuthenticatedUser;
        this.repository = repository;
    }

    @Transactional
    public List<AccessibleOrganization> execute() {
        AuthenticatedUser authenticatedUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new IllegalStateException("A identidade autenticada não está disponível"));
        synchronizeAuthenticatedUser.execute(authenticatedUser);
        return repository.findActiveForCurrentUser(authenticatedUser.userId());
    }
}
