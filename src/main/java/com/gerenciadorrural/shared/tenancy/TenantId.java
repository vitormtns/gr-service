package com.gerenciadorrural.shared.tenancy;

import java.util.Objects;
import java.util.UUID;

/** Identifica a futura Organization que delimita o tenant. */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "O identificador do tenant é obrigatório");
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    public static TenantId from(String value) {
        Objects.requireNonNull(value, "O identificador do tenant é obrigatório");
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
