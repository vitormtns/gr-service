package com.gerenciadorrural.modules.herd.domain;
import java.time.*; import java.util.*;
public record PaddockMovement(UUID eventId, UUID animalId, String identification, UUID sourcePaddockId, String sourcePaddockName, UUID destinationPaddockId, String destinationPaddockName, LocalDate occurredOn, Instant recordedAt, UUID actorUserId) {}
