package com.gerenciadorrural.modules.identity.application;

import com.gerenciadorrural.modules.identity.domain.InternalUser;
import com.gerenciadorrural.modules.identity.domain.InternalUserRepository;
import com.gerenciadorrural.modules.identity.domain.InternalUserStatus;
import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class SynchronizeAuthenticatedUser {

    static final int MAX_UPDATE_ATTEMPTS = 3;
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final InternalUserRepository repository;
    private final Clock clock;

    public SynchronizeAuthenticatedUser(InternalUserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public InternalUser execute(AuthenticatedUser authenticatedUser) {
        Optional<String> trustedEmail = authenticatedUser.email().flatMap(SynchronizeAuthenticatedUser::validEmail);

        for (int attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            Optional<InternalUser> found = repository.findById(authenticatedUser.userId());
            InternalUser current;
            if (found.isEmpty()) {
                Instant now = now();
                InternalUser newUser = new InternalUser(
                        authenticatedUser.userId(),
                        trustedEmail,
                        Optional.empty(),
                        InternalUserStatus.ACTIVE,
                        now,
                        now,
                        0
                );
                if (repository.insert(newUser)) {
                    return newUser;
                }
                current = repository.findById(authenticatedUser.userId())
                        .orElseThrow(InternalUserConflictException::new);
            } else {
                current = found.get();
            }

            ensureAccessAllowed(current);
            Optional<String> desiredEmail = trustedEmail.isPresent() ? trustedEmail : current.email();
            Optional<String> desiredDisplayName = current.displayName();
            if (desiredEmail.equals(current.email()) && desiredDisplayName.equals(current.displayName())) {
                return current;
            }

            Optional<InternalUser> updated = repository.updateIdentity(
                    current.id(), desiredEmail, desiredDisplayName, now(), current.version());
            if (updated.isPresent()) {
                return updated.get();
            }
            if (repository.findById(current.id()).isEmpty()) {
                throw new InternalUserConflictException();
            }
        }
        throw new InternalUserConflictException();
    }

    private static Optional<String> validEmail(String rawEmail) {
        String email = rawEmail.trim();
        return email.length() <= MAX_EMAIL_LENGTH && EMAIL_PATTERN.matcher(email).matches()
                ? Optional.of(email)
                : Optional.empty();
    }

    private static void ensureAccessAllowed(InternalUser user) {
        switch (user.status()) {
            case ACTIVE -> {
                return;
            }
            case SUSPENDED -> throw new InternalUserSuspendedException();
            case DEACTIVATED -> throw new InternalUserDeactivatedException();
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
