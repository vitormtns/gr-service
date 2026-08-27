package com.gerenciadorrural.shared.security.infrastructure;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class SupabaseJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "O token não contém os claims obrigatórios aceitos", null);

    private final Set<String> acceptedAudiences;
    private final Set<String> acceptedTokenRoles;
    private final Duration clockSkew;

    public SupabaseJwtValidator(
            Set<String> acceptedAudiences,
            Set<String> acceptedTokenRoles,
            Duration clockSkew
    ) {
        this.acceptedAudiences = Set.copyOf(acceptedAudiences);
        this.acceptedTokenRoles = Set.copyOf(acceptedTokenRoles);
        this.clockSkew = clockSkew;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!hasRequiredClaims(jwt)
                || !hasUuidSubject(jwt)
                || jwt.getIssuedAt().isAfter(Instant.now().plus(clockSkew))
                || jwt.getAudience().stream().noneMatch(acceptedAudiences::contains)
                || !acceptedTokenRoles.contains(jwt.getClaimAsString("role"))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean hasRequiredClaims(Jwt jwt) {
        return jwt.getSubject() != null
                && jwt.getIssuer() != null
                && jwt.getExpiresAt() != null
                && jwt.getIssuedAt() != null;
    }

    private static boolean hasUuidSubject(Jwt jwt) {
        try {
            UUID.fromString(jwt.getSubject());
            return true;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }
}
