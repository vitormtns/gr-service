package com.gerenciadorrural.modules.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InternalUser(
        UUID id,
        Optional<String> email,
        Optional<String> displayName,
        InternalUserStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public InternalUser {
        Objects.requireNonNull(id, "id é obrigatório");
        email = Objects.requireNonNull(email, "email é obrigatório");
        displayName = Objects.requireNonNull(displayName, "displayName é obrigatório");
        Objects.requireNonNull(status, "status é obrigatório");
        Objects.requireNonNull(createdAt, "createdAt é obrigatório");
        Objects.requireNonNull(updatedAt, "updatedAt é obrigatório");
        if (version < 0) {
            throw new IllegalArgumentException("version não pode ser negativa");
        }
    }
}
