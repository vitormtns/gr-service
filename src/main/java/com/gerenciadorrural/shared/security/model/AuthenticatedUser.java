package com.gerenciadorrural.shared.security.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        Optional<String> email,
        Optional<String> sessionId,
        Optional<String> authenticationLevel,
        Instant issuedAt,
        Instant expiresAt
) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId é obrigatório");
        email = Objects.requireNonNull(email, "email é obrigatório");
        sessionId = Objects.requireNonNull(sessionId, "sessionId é obrigatório");
        authenticationLevel = Objects.requireNonNull(authenticationLevel, "authenticationLevel é obrigatório");
        Objects.requireNonNull(issuedAt, "issuedAt é obrigatório");
        Objects.requireNonNull(expiresAt, "expiresAt é obrigatório");
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId
                + ", email=[protegido], sessionId=[protegido], authenticationLevel=" + authenticationLevel
                + ", issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }
}
