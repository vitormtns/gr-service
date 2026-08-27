package com.gerenciadorrural.modules.identity.application;

import com.gerenciadorrural.modules.identity.domain.InternalUser;
import com.gerenciadorrural.modules.identity.domain.InternalUserRepository;
import com.gerenciadorrural.modules.identity.domain.InternalUserStatus;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SynchronizeAuthenticatedUserTest {

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("a6d8d651-b315-44a2-95f3-327682febd08");

    @Mock
    private InternalUserRepository repository;

    private SynchronizeAuthenticatedUser synchronize;

    @BeforeEach
    void setUp() {
        synchronize = new SynchronizeAuthenticatedUser(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsANewActiveUserFromTheValidatedSubject() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        when(repository.insert(any())).thenReturn(true);

        InternalUser result = synchronize.execute(authenticated(Optional.of(" person@example.test ")));

        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.email()).contains("person@example.test");
        assertThat(result.displayName()).isEmpty();
        assertThat(result.status()).isEqualTo(InternalUserStatus.ACTIVE);
        assertThat(result.version()).isZero();
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void returnsExistingUserWithoutWritingWhenIdentityDidNotChange() {
        InternalUser current = user(Optional.of("person@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 4);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(current));

        InternalUser result = synchronize.execute(authenticated(Optional.of("person@example.test")));

        assertThat(result).isSameAs(current);
        verify(repository, never()).updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void updatesAChangedValidEmailUsingOptimisticLocking() {
        InternalUser current = user(Optional.of("old@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 2);
        InternalUser updated = user(Optional.of("new@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 3);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(current));
        when(repository.updateIdentity(USER_ID, updated.email(), updated.displayName(), NOW, 2))
                .thenReturn(Optional.of(updated));

        assertThat(synchronize.execute(authenticated(updated.email()))).isEqualTo(updated);
    }

    @Test
    void absentEmailDoesNotErasePersistedEmailOrChangeVersion() {
        InternalUser current = user(Optional.of("person@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 7);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(current));

        InternalUser result = synchronize.execute(authenticated(Optional.empty()));

        assertThat(result.email()).contains("person@example.test");
        assertThat(result.version()).isEqualTo(7);
        verify(repository, never()).updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void invalidEmailDoesNotOverwritePersistedEmail() {
        InternalUser current = user(Optional.of("person@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 1);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(current));

        assertThat(synchronize.execute(authenticated(Optional.of("not-an-email")))).isSameAs(current);
        verify(repository, never()).updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void absentTrustedDisplayNameDoesNotErasePersistedValue() {
        InternalUser current = user(
                Optional.of("person@example.test"), Optional.of("Nome persistido"), InternalUserStatus.ACTIVE, 1);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(current));

        assertThat(synchronize.execute(authenticated(current.email())).displayName()).contains("Nome persistido");
        verify(repository, never()).updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void suspendedUserIsNeverReactivated() {
        when(repository.findById(USER_ID))
                .thenReturn(Optional.of(user(Optional.empty(), Optional.empty(), InternalUserStatus.SUSPENDED, 3)));

        assertThatThrownBy(() -> synchronize.execute(authenticated(Optional.of("person@example.test"))))
                .isInstanceOf(InternalUserSuspendedException.class);
        verify(repository, never()).updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void deactivatedUserIsNeverReactivated() {
        when(repository.findById(USER_ID))
                .thenReturn(Optional.of(user(Optional.empty(), Optional.empty(), InternalUserStatus.DEACTIVATED, 3)));

        assertThatThrownBy(() -> synchronize.execute(authenticated(Optional.of("person@example.test"))))
                .isInstanceOf(InternalUserDeactivatedException.class);
        verify(repository, never()).updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void reloadsAfterConcurrentInsertInsteadOfFailing() {
        InternalUser concurrent = user(Optional.of("person@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 0);
        when(repository.findById(USER_ID)).thenReturn(Optional.empty(), Optional.of(concurrent));
        when(repository.insert(any())).thenReturn(false);

        assertThat(synchronize.execute(authenticated(concurrent.email()))).isEqualTo(concurrent);
    }

    @Test
    void retriesAnOptimisticLockConflictAndUsesTheLatestVersion() {
        InternalUser versionOne = user(Optional.of("old@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 1);
        InternalUser versionTwo = user(Optional.of("other@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 2);
        InternalUser versionThree = user(Optional.of("new@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 3);
        when(repository.findById(USER_ID))
                .thenReturn(Optional.of(versionOne), Optional.of(versionTwo), Optional.of(versionTwo));
        when(repository.updateIdentity(USER_ID, versionThree.email(), versionThree.displayName(), NOW, 1))
                .thenReturn(Optional.empty());
        when(repository.updateIdentity(USER_ID, versionThree.email(), versionThree.displayName(), NOW, 2))
                .thenReturn(Optional.of(versionThree));

        assertThat(synchronize.execute(authenticated(versionThree.email()))).isEqualTo(versionThree);
        verify(repository, times(2)).updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void limitsOptimisticLockRetries() {
        InternalUser current = user(Optional.of("old@example.test"), Optional.empty(), InternalUserStatus.ACTIVE, 1);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(current));
        when(repository.updateIdentity(any(), any(), any(), any(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> synchronize.execute(authenticated(Optional.of("new@example.test"))))
                .isInstanceOf(InternalUserConflictException.class);
        verify(repository, times(SynchronizeAuthenticatedUser.MAX_UPDATE_ATTEMPTS))
                .updateIdentity(any(), any(), any(), any(), anyLong());
    }

    @Test
    void repositoryReceivesOnlyTheInternalIdentityModel() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        when(repository.insert(any())).thenReturn(true);
        ArgumentCaptor<InternalUser> captor = ArgumentCaptor.forClass(InternalUser.class);

        synchronize.execute(authenticated(Optional.of("person@example.test")));

        verify(repository).insert(captor.capture());
        assertThat(Arrays.stream(captor.getValue().getClass().getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("id", "email", "displayName", "status", "createdAt", "updatedAt", "version");
    }

    private static AuthenticatedUser authenticated(Optional<String> email) {
        return new AuthenticatedUser(
                USER_ID,
                email,
                Optional.of("session-id"),
                Optional.of("aal1"),
                NOW.minusSeconds(10),
                NOW.plusSeconds(300)
        );
    }

    private static InternalUser user(
            Optional<String> email,
            Optional<String> displayName,
            InternalUserStatus status,
            long version
    ) {
        return new InternalUser(USER_ID, email, displayName, status, NOW.minusSeconds(60), NOW, version);
    }
}
