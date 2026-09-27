package com.gerenciadorrural.modules.herd.domain;

import java.time.LocalDate;

public record AnimalNoteEventDetails(LocalDate occurredOn, String notes, long expectedVersion)
        implements AnimalEventDetails {}
