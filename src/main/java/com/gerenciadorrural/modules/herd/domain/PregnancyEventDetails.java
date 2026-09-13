package com.gerenciadorrural.modules.herd.domain;
import java.time.LocalDate; import java.util.UUID;
public record PregnancyEventDetails(UUID pregnancyId,LocalDate occurredOn,PregnancyTerminationReason terminationReason) implements AnimalEventDetails {}
