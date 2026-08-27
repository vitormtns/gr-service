package com.gerenciadorrural.shared.security.api;

import com.gerenciadorrural.shared.security.model.AuthenticatedUser;

import java.time.Instant;

public record CurrentUserResponse(
        String userId,
        String email,
        String sessionId,
        String authenticationLevel,
        Instant issuedAt,
        Instant expiresAt
) {

    static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.userId().toString(),
                user.email().orElse(null),
                user.sessionId().orElse(null),
                user.authenticationLevel().orElse(null),
                user.issuedAt(),
                user.expiresAt()
        );
    }

    @Override
    public String toString() {
        return "CurrentUserResponse[userId=" + userId
                + ", email=[protegido], sessionId=[protegido], authenticationLevel=" + authenticationLevel
                + ", issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }
}
