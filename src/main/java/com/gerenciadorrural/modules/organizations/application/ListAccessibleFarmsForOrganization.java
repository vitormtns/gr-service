package com.gerenciadorrural.modules.organizations.application;

import com.gerenciadorrural.modules.identity.application.SynchronizeAuthenticatedUser;
import com.gerenciadorrural.modules.organizations.domain.AccessibleFarm;
import com.gerenciadorrural.modules.organizations.domain.AccessibleFarmRepository;
import com.gerenciadorrural.shared.security.application.CurrentUserProvider;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListAccessibleFarmsForOrganization {

    private final CurrentUserProvider currentUserProvider;
    private final SynchronizeAuthenticatedUser synchronizeAuthenticatedUser;
    private final AccessibleFarmRepository repository;

    public ListAccessibleFarmsForOrganization(
            CurrentUserProvider currentUserProvider,
            SynchronizeAuthenticatedUser synchronizeAuthenticatedUser,
            AccessibleFarmRepository repository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.synchronizeAuthenticatedUser = synchronizeAuthenticatedUser;
        this.repository = repository;
    }

    @Transactional
    public List<AccessibleFarm> execute(UUID organizationId) {
        AuthenticatedUser authenticatedUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new IllegalStateException("A identidade autenticada não está disponível"));
        synchronizeAuthenticatedUser.execute(authenticatedUser);
        return repository.findForCurrentUserAndOrganization(authenticatedUser.userId(), organizationId);
    }
}
