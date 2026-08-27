package com.gerenciadorrural.modules.identity.api;

import com.gerenciadorrural.modules.identity.application.SynchronizedCurrentUser;

import java.time.Instant;

public record CurrentUserResponse(
        String userId,
        String email,
        String displayName,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version,
        AuthenticationResponse authentication
) {

    static CurrentUserResponse from(SynchronizedCurrentUser currentUser) {
        var internal = currentUser.internalUser();
        var authentication = currentUser.authentication();
        return new CurrentUserResponse(
                internal.id().toString(),
                internal.email().orElse(null),
                internal.displayName().orElse(null),
                internal.status().name(),
                internal.createdAt(),
                internal.updatedAt(),
                internal.version(),
                new AuthenticationResponse(
                        authentication.sessionId().orElse(null),
                        authentication.authenticationLevel().orElse(null),
                        authentication.issuedAt(),
                        authentication.expiresAt()
                )
        );
    }

    public record AuthenticationResponse(
            String sessionId,
            String authenticationLevel,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }

    @Override
    public String toString() {
        return "CurrentUserResponse[userId=" + userId
                + ", email=[protegido], displayName=[protegido], status=" + status
                + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + ", version=" + version
                + ", authentication=[protegida]]";
    }
}
