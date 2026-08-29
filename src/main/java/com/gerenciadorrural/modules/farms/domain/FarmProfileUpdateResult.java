package com.gerenciadorrural.modules.farms.domain;

import java.util.Objects;

public sealed interface FarmProfileUpdateResult permits FarmProfileUpdateResult.Updated,
        FarmProfileUpdateResult.VersionConflict, FarmProfileUpdateResult.NotAvailable {

    record Updated(FarmProfile profile) implements FarmProfileUpdateResult {
        public Updated {
            Objects.requireNonNull(profile, "O perfil atualizado é obrigatório");
        }
    }

    record VersionConflict() implements FarmProfileUpdateResult {
    }

    record NotAvailable() implements FarmProfileUpdateResult {
    }
}
