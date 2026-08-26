package com.gerenciadorrural.shared.tenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Contexto validado da requisição; não deve ser construído com um tenant livre enviado pelo cliente. */
public record TenantContext(
        TenantId tenantId,
        Optional<String> farmId,
        Optional<String> userId,
        Optional<String> deviceId,
        UUID correlationId
) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        farmId = Objects.requireNonNull(farmId, "farmId não pode ser nulo");
        userId = Objects.requireNonNull(userId, "userId não pode ser nulo");
        deviceId = Objects.requireNonNull(deviceId, "deviceId não pode ser nulo");
        Objects.requireNonNull(correlationId, "O correlation ID é obrigatório");
    }
}
