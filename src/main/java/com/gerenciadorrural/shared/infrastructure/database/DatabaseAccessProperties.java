package com.gerenciadorrural.shared.infrastructure.database;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.database")
public record DatabaseAccessProperties(
        @NotBlank String schema,
        @NotBlank String runtimeRole
) {

    public DatabaseAccessProperties {
        if (!"app".equals(schema)) {
            throw new IllegalArgumentException("O schema configurado para a aplicação deve ser app");
        }
        if (!"app_api".equals(runtimeRole)) {
            throw new IllegalArgumentException("A role runtime configurada deve ser app_api");
        }
    }
}
