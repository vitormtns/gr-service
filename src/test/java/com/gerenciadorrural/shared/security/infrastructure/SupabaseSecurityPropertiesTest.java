package com.gerenciadorrural.shared.security.infrastructure;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseSecurityPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void jwksModeRequiresJwksUri() {
        SupabaseSecurityProperties properties = properties(
                SupabaseSecurityProperties.Mode.JWKS, SupabaseSecurityProperties.Algorithm.ES256, null, null);

        assertThat(messages(properties)).contains("a configuração de JWKS e HMAC é contraditória ou incompleta");
    }

    @Test
    void hmacModeRequiresSecret() {
        SupabaseSecurityProperties properties = properties(
                SupabaseSecurityProperties.Mode.HMAC, SupabaseSecurityProperties.Algorithm.HS256, null, null);

        assertThat(messages(properties)).contains("a configuração de JWKS e HMAC é contraditória ou incompleta");
    }

    @Test
    void hmacModeRejectsJwksConfiguration() {
        SupabaseSecurityProperties properties = properties(
                SupabaseSecurityProperties.Mode.HMAC, SupabaseSecurityProperties.Algorithm.HS256,
                "https://auth.example.test/jwks", "a-secure-secret-with-at-least-32-bytes");

        assertThat(messages(properties)).contains("a configuração de JWKS e HMAC é contraditória ou incompleta");
    }

    @Test
    void jwksModeRejectsHmacSecret() {
        SupabaseSecurityProperties properties = properties(
                SupabaseSecurityProperties.Mode.JWKS, SupabaseSecurityProperties.Algorithm.ES256,
                "https://auth.example.test/jwks", "a-secure-secret-with-at-least-32-bytes");

        assertThat(messages(properties)).contains("a configuração de JWKS e HMAC é contraditória ou incompleta");
    }

    @Test
    void weakHmacSecretFailsWithoutLeakingItsValue() {
        String weakSecret = "valor-curto-sensivel";
        SupabaseSecurityProperties properties = properties(
                SupabaseSecurityProperties.Mode.HMAC, SupabaseSecurityProperties.Algorithm.HS256, null, weakSecret);

        String allMessages = String.join(" ", messages(properties));
        assertThat(allMessages)
                .contains("o segredo HMAC deve possuir pelo menos 32 bytes")
                .doesNotContain(weakSecret);
        assertThat(properties.toString()).doesNotContain(weakSecret).contains("[protegido]");
    }

    @Test
    void issuerIsRequired() {
        SupabaseSecurityProperties properties = new SupabaseSecurityProperties(
                SupabaseSecurityProperties.Mode.JWKS,
                SupabaseSecurityProperties.Algorithm.ES256,
                " ",
                "https://auth.example.test/jwks",
                null,
                Set.of("authenticated"),
                Set.of("authenticated"),
                Duration.ofSeconds(30));

        assertThat(messages(properties)).anyMatch(message -> message.contains("issuer"));
    }

    @Test
    void algorithmMustMatchMode() {
        SupabaseSecurityProperties properties = properties(
                SupabaseSecurityProperties.Mode.JWKS, SupabaseSecurityProperties.Algorithm.HS256,
                "https://auth.example.test/jwks", null);

        assertThat(messages(properties)).contains("o algoritmo configurado é incompatível com o modo de validação");
    }

    private SupabaseSecurityProperties properties(
            SupabaseSecurityProperties.Mode mode,
            SupabaseSecurityProperties.Algorithm algorithm,
            String jwksUri,
            String hmacSecret
    ) {
        return new SupabaseSecurityProperties(
                mode,
                algorithm,
                "https://auth.example.test/auth/v1",
                jwksUri,
                hmacSecret,
                Set.of("authenticated"),
                Set.of("authenticated"),
                Duration.ofSeconds(30));
    }

    private Set<String> messages(SupabaseSecurityProperties properties) {
        return validator.validate(properties).stream()
                .map(violation -> violation.getMessage())
                .collect(java.util.stream.Collectors.toSet());
    }
}
