package com.gerenciadorrural.modules.herd.domain;
import java.time.*; import java.util.*;
public record AnimalEvent(UUID id, UUID animalId, AnimalEventType type, UUID operationId, UUID actorUserId, LocalDate occurredOn, Instant recordedAt, long resultingVersion, AnimalEventDetails details) {}
