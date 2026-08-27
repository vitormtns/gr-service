package com.gerenciadorrural.shared.tenancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @Test
    void shouldRejectNullOptionalInsteadOfHidingAnInvalidContext() {
        assertThatThrownBy(() -> new TenantContext(
                TenantId.generate(), null, UUID.randomUUID(), UUID.randomUUID(), "VIEWER", "ALL_FARMS"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O usuário é obrigatório");
    }
}
