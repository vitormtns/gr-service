package com.gerenciadorrural.shared.security.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("app.http")
public record HttpSecurityProperties(@Valid @NotNull Cors cors) {

    public HttpSecurityProperties {
        cors = cors == null ? new Cors(List.of(), false, Duration.ofMinutes(30)) : cors;
    }

    public record Cors(
            List<String> allowedOrigins,
            boolean allowCredentials,
            @NotNull Duration maxAge
    ) {
        public Cors {
            allowedOrigins = allowedOrigins == null
                    ? List.of()
                    : allowedOrigins.stream().filter(value -> value != null && !value.isBlank()).toList();
        }

        @AssertTrue(message = "as origens CORS devem ser HTTP/HTTPS, explícitas e sem caminho")
        public boolean isAllowedOriginsValid() {
            return allowedOrigins.stream().allMatch(Cors::isOrigin);
        }

        @AssertTrue(message = "CORS com credenciais não permite origem curinga")
        public boolean isCredentialPolicyValid() {
            return !allowCredentials || allowedOrigins.stream().noneMatch("*"::equals);
        }

        @AssertTrue(message = "o tempo de cache do preflight CORS deve estar entre zero e 24 horas")
        public boolean isMaxAgeValid() {
            return maxAge != null && !maxAge.isNegative() && maxAge.compareTo(Duration.ofHours(24)) <= 0;
        }

        private static boolean isOrigin(String value) {
            if ("*".equals(value)) {
                return false;
            }
            try {
                URI uri = URI.create(value);
                return uri.isAbsolute()
                        && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null
                        && (uri.getPath() == null || uri.getPath().isEmpty())
                        && uri.getQuery() == null
                        && uri.getFragment() == null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }
}
