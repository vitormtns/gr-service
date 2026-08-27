package com.gerenciadorrural.shared.tenancy;

import java.util.Objects;
import java.util.UUID;

/** Contexto validado da requisição; não deve ser construído com um tenant livre enviado pelo cliente. */
public record TenantContext(
        TenantId tenantId,
        UUID userId,
        UUID farmId,
        UUID membershipId,
        String role,
        String farmScopeMode
) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(userId, "O usuário é obrigatório"); Objects.requireNonNull(farmId, "A fazenda é obrigatória");
        Objects.requireNonNull(membershipId, "O membership é obrigatório"); Objects.requireNonNull(role, "O papel é obrigatório"); Objects.requireNonNull(farmScopeMode, "O escopo é obrigatório");
    }
}
