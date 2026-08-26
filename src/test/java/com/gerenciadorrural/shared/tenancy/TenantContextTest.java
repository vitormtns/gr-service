package com.gerenciadorrural.shared.tenancy;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @Test
    void shouldRejectNullOptionalInsteadOfHidingAnInvalidContext() {
        assertThatThrownBy(() -> new TenantContext(
                TenantId.generate(), null, Optional.empty(), Optional.empty(), UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("farmId não pode ser nulo");
    }
}
