package com.gerenciadorrural.modules.organizations.application;

import com.gerenciadorrural.modules.identity.application.SynchronizeAuthenticatedUser;
import com.gerenciadorrural.modules.organizations.domain.*;
import com.gerenciadorrural.shared.security.application.CurrentUserProvider;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*; import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResolveTenantContextTest {
 @Mock CurrentUserProvider users; @Mock SynchronizeAuthenticatedUser synchronize; @Mock TenantContextResolverRepository repository;
 @Test void synchronizesThenBuildsContextFromPersistedMembershipValues(){UUID userId=UUID.randomUUID(),org=UUID.randomUUID(),farm=UUID.randomUUID(),membership=UUID.randomUUID(); var user=new AuthenticatedUser(userId,Optional.empty(),Optional.empty(),Optional.empty(),Instant.now(),Instant.now().plusSeconds(60)); var row=new ResolvedTenantContext(org,"Organização",farm,"Fazenda",membership,"MANAGER","SELECTED_FARMS");when(users.currentUser()).thenReturn(Optional.of(user));when(repository.resolve(userId,org,farm)).thenReturn(Optional.of(row));var result=new ResolveTenantContext(users,synchronize,repository).execute(org,farm);assertThat(result.context().role()).isEqualTo("MANAGER");assertThat(result.context().farmScopeMode()).isEqualTo("SELECTED_FARMS");InOrder order=inOrder(users,synchronize,repository);order.verify(users).currentUser();order.verify(synchronize).execute(user);order.verify(repository).resolve(userId,org,farm);}
 @Test void inaccessibleCombinationUsesOneGenericException(){UUID org=UUID.randomUUID(),farm=UUID.randomUUID(),userId=UUID.randomUUID();when(users.currentUser()).thenReturn(Optional.of(new AuthenticatedUser(userId,Optional.empty(),Optional.empty(),Optional.empty(),Instant.now(),Instant.now().plusSeconds(60))));when(repository.resolve(userId,org,farm)).thenReturn(Optional.empty());assertThatThrownBy(()->new ResolveTenantContext(users,synchronize,repository).execute(org,farm)).isInstanceOf(TenantContextNotAvailableException.class);}
}
