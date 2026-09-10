package com.gerenciadorrural.modules.herd.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record CorrectedEventDetails(Map<String, FieldChange> changes) implements AnimalEventDetails {
    public CorrectedEventDetails {
        changes = Map.copyOf(new LinkedHashMap<>(changes));
    }
}
