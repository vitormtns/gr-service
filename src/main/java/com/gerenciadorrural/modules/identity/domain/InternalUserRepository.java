package com.gerenciadorrural.modules.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InternalUserRepository {

    Optional<InternalUser> findById(UUID id);

    boolean insert(InternalUser user);

    Optional<InternalUser> updateIdentity(
            UUID id,
            Optional<String> email,
            Optional<String> displayName,
            Instant updatedAt,
            long expectedVersion
    );
}
