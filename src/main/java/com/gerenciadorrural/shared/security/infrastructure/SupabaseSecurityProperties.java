package com.gerenciadorrural.shared.security.infrastructure;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties("app.security.supabase")
public record SupabaseSecurityProperties(
        @NotNull Mode mode,
        @NotNull Algorithm algorithm,
        @NotBlank String issuer,
        String jwksUri,
        String hmacSecret,
        @NotEmpty Set<@NotBlank String> audiences,
        @NotEmpty Set<@NotBlank String> acceptedTokenRoles,
        @NotNull Duration clockSkew
) {

    public SupabaseSecurityProperties {
        audiences = audiences == null ? null : Set.copyOf(audiences);
        acceptedTokenRoles = acceptedTokenRoles == null ? null : Set.copyOf(acceptedTokenRoles);
    }

    @AssertTrue(message = "issuer deve ser uma URI HTTP ou HTTPS absoluta")
    public boolean isIssuerValid() {
        return isHttpUri(issuer);
    }

    @AssertTrue(message = "a configuração de JWKS e HMAC é contraditória ou incompleta")
    public boolean isModeConfigurationValid() {
        if (mode == null) {
            return true;
        }
        return switch (mode) {
            case JWKS -> hasText(jwksUri) && isHttpUri(jwksUri) && !hasText(hmacSecret);
            case HMAC -> hasText(hmacSecret) && !hasText(jwksUri);
        };
    }

    @AssertTrue(message = "o algoritmo configurado é incompatível com o modo de validação")
    public boolean isAlgorithmCompatible() {
        if (mode == null || algorithm == null) {
            return true;
        }
        return mode == Mode.HMAC ? algorithm == Algorithm.HS256 : algorithm != Algorithm.HS256;
    }

    @AssertTrue(message = "o segredo HMAC deve possuir pelo menos 32 bytes")
    public boolean isHmacSecretStrongEnough() {
        return mode != Mode.HMAC || hmacSecret == null
                || hmacSecret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    @AssertTrue(message = "clock skew deve estar entre zero e dois minutos")
    public boolean isClockSkewValid() {
        return clockSkew != null && !clockSkew.isNegative() && clockSkew.compareTo(Duration.ofMinutes(2)) <= 0;
    }

    @Override
    public String toString() {
        return "SupabaseSecurityProperties[mode=" + mode
                + ", algorithm=" + algorithm
                + ", issuer=" + issuer
                + ", jwksUri=" + jwksUri
                + ", hmacSecret=[protegido]"
                + ", audiences=" + audiences
                + ", acceptedTokenRoles=" + acceptedTokenRoles
                + ", clockSkew=" + clockSkew + "]";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isHttpUri(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public enum Mode {
        JWKS,
        HMAC
    }

    public enum Algorithm {
        HS256,
        RS256,
        ES256
    }
}
