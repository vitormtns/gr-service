package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Dados já normalizados para a inserção de um animal; defaults técnicos pertencem ao banco. */
public record NewHerdAnimal(
        UUID id,
        TenantId tenantId,
        UUID farmId,
        String identification,
        String name,
        HerdAnimalSex sex,
        LocalDate birthDate
) {

    public NewHerdAnimal {
        Objects.requireNonNull(id, "O ID do animal é obrigatório");
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(farmId, "A fazenda é obrigatória");
        Objects.requireNonNull(identification, "A identificação é obrigatória");
        Objects.requireNonNull(sex, "O sexo é obrigatório");
    }
}
