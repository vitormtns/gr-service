package com.gerenciadorrural.shared.domain.event;

import com.gerenciadorrural.shared.tenancy.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Fato imutável ocorrido no domínio. Este contrato não implica persistência nem Event Sourcing.
 */
public interface DomainEvent {

    UUID eventId();

    String eventType();

    int eventVersion();

    Instant occurredAt();

    String aggregateId();

    String aggregateType();

    long aggregateVersion();

    TenantId tenantId();

    Optional<String> farmId();

    Optional<String> userId();

    Optional<String> deviceId();

    UUID correlationId();

    Optional<UUID> causationId();
}
