package com.gerenciadorrural.modules.organizations.application;

import com.gerenciadorrural.modules.identity.application.SynchronizeAuthenticatedUser;
import com.gerenciadorrural.modules.organizations.domain.AccessibleFarm;
import com.gerenciadorrural.modules.organizations.domain.AccessibleFarmRepository;
import com.gerenciadorrural.shared.security.application.CurrentUserProvider;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAccessibleFarmsForOrganizationTest {
    @Mock CurrentUserProvider currentUserProvider;
    @Mock SynchronizeAuthenticatedUser synchronizeAuthenticatedUser;
    @Mock AccessibleFarmRepository repository;

    @Test
    void synchronizesBeforeListingAndPassesOnlyValidatedUserAndOrganizationIds() {
        UUID userId = UUID.randomUUID(); UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, Optional.empty(), Optional.empty(), Optional.empty(), Instant.now(), Instant.now().plusSeconds(60));
        List<AccessibleFarm> farms = List.of(new AccessibleFarm(UUID.randomUUID(), "Fazenda"));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(user));
        when(repository.findForCurrentUserAndOrganization(userId, organizationId)).thenReturn(farms);
        assertThat(new ListAccessibleFarmsForOrganization(currentUserProvider, synchronizeAuthenticatedUser, repository).execute(organizationId)).isEqualTo(farms);
        InOrder order = inOrder(currentUserProvider, synchronizeAuthenticatedUser, repository);
        order.verify(currentUserProvider).currentUser(); order.verify(synchronizeAuthenticatedUser).execute(user); order.verify(repository).findForCurrentUserAndOrganization(userId, organizationId);
    }

    @Test
    void preservesAnEmptyResult() {
        UUID userId = UUID.randomUUID(); UUID organizationId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, Optional.empty(), Optional.empty(), Optional.empty(), Instant.now(), Instant.now().plusSeconds(60));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(user)); when(repository.findForCurrentUserAndOrganization(userId, organizationId)).thenReturn(List.of());
        assertThat(new ListAccessibleFarmsForOrganization(currentUserProvider, synchronizeAuthenticatedUser, repository).execute(organizationId)).isEmpty();
    }
}
