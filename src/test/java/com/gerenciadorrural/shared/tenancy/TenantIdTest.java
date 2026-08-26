package com.gerenciadorrural.shared.tenancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdTest {

    @Test
    void shouldRecreateTenantIdFromItsTextRepresentation() {
        UUID value = UUID.randomUUID();

        TenantId tenantId = TenantId.from(value.toString());

        assertThat(tenantId.value()).isEqualTo(value);
        assertThat(tenantId).hasToString(value.toString());
    }

    @Test
    void shouldGenerateDifferentIdentifiers() {
        assertThat(TenantId.generate()).isNotEqualTo(TenantId.generate());
    }

    @Test
    void shouldRejectInvalidIdentifier() {
        assertThatThrownBy(() -> TenantId.from("tenant-livre"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
