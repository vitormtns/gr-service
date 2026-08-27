package com.gerenciadorrural.modules.organizations.application;

import com.gerenciadorrural.modules.identity.application.SynchronizeAuthenticatedUser;
import com.gerenciadorrural.modules.identity.domain.InternalUser;
import com.gerenciadorrural.modules.identity.domain.InternalUserStatus;
import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganization;
import com.gerenciadorrural.modules.organizations.domain.AccessibleOrganizationRepository;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCurrentUserOrganizationsTest {

    private static final UUID USER_ID = UUID.fromString("cf606f60-7dd4-4fd5-8113-a9278bddc5dc");

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private SynchronizeAuthenticatedUser synchronizeAuthenticatedUser;

    @Mock
    private AccessibleOrganizationRepository repository;

    @Test
    void synchronizesTheAuthenticatedUserBeforeListingOrganizations() {
        AuthenticatedUser authenticatedUser = authenticatedUser();
        List<AccessibleOrganization> organizations = List.of(organization("Organização A", "OWNER", "ALL_FARMS"));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(authenticatedUser));
        when(synchronizeAuthenticatedUser.execute(authenticatedUser)).thenReturn(internalUser());
        when(repository.findActiveForCurrentUser(USER_ID)).thenReturn(organizations);

        assertThat(new ListCurrentUserOrganizations(currentUserProvider, synchronizeAuthenticatedUser, repository).execute())
                .isEqualTo(organizations);

        InOrder order = inOrder(currentUserProvider, synchronizeAuthenticatedUser, repository);
        order.verify(currentUserProvider).currentUser();
        order.verify(synchronizeAuthenticatedUser).execute(authenticatedUser);
        order.verify(repository).findActiveForCurrentUser(USER_ID);
    }

    @Test
    void returnsAnEmptyListWhenTheAuthenticatedUserHasNoMemberships() {
        AuthenticatedUser authenticatedUser = authenticatedUser();
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(authenticatedUser));
        when(synchronizeAuthenticatedUser.execute(authenticatedUser)).thenReturn(internalUser());
        when(repository.findActiveForCurrentUser(USER_ID)).thenReturn(List.of());

        assertThat(new ListCurrentUserOrganizations(currentUserProvider, synchronizeAuthenticatedUser, repository).execute())
                .isEmpty();
    }

    @Test
    void preservesRolesAndFarmScopeModesFromTheMembershipReadModel() {
        AuthenticatedUser authenticatedUser = authenticatedUser();
        List<AccessibleOrganization> organizations = List.of(
                organization("Organização A", "OWNER", "ALL_FARMS"),
                organization("Organização B", "VIEWER", "SELECTED_FARMS")
        );
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(authenticatedUser));
        when(synchronizeAuthenticatedUser.execute(authenticatedUser)).thenReturn(internalUser());
        when(repository.findActiveForCurrentUser(USER_ID)).thenReturn(organizations);

        assertThat(new ListCurrentUserOrganizations(currentUserProvider, synchronizeAuthenticatedUser, repository).execute())
                .extracting(AccessibleOrganization::role, AccessibleOrganization::farmScopeMode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("OWNER", "ALL_FARMS"),
                        org.assertj.core.groups.Tuple.tuple("VIEWER", "SELECTED_FARMS")
                );
        verifyNoMoreInteractions(repository);
    }

    private static AuthenticatedUser authenticatedUser() {
        Instant now = Instant.parse("2026-08-27T10:00:00Z");
        return new AuthenticatedUser(USER_ID, Optional.of("pessoa@example.test"), Optional.empty(), Optional.empty(),
                now.minusSeconds(30), now.plusSeconds(300));
    }

    private static InternalUser internalUser() {
        Instant now = Instant.parse("2026-08-27T10:00:00Z");
        return new InternalUser(USER_ID, Optional.of("pessoa@example.test"), Optional.empty(),
                InternalUserStatus.ACTIVE, now, now, 0);
    }

    private static AccessibleOrganization organization(String name, String role, String farmScopeMode) {
        return new AccessibleOrganization(UUID.randomUUID(), name, UUID.randomUUID(), role, farmScopeMode);
    }
}
