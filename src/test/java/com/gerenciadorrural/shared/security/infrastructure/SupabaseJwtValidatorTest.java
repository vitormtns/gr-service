package com.gerenciadorrural.shared.security.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseJwtValidatorTest {

    private final SupabaseJwtValidator validator =
            new SupabaseJwtValidator(
                    Set.of("authenticated"), Set.of("authenticated"), Duration.ofSeconds(30));

    @Test
    void validUuidSubjectAndAcceptedAudienceAreAccepted() {
        assertThat(validator.validate(jwt(UUID.randomUUID().toString(), List.of("authenticated"))).hasErrors())
                .isFalse();
    }

    @Test
    void missingSubjectIsRejected() {
        assertThat(validator.validate(jwt(null, List.of("authenticated"))).hasErrors()).isTrue();
    }

    @Test
    void invalidUuidSubjectIsRejected() {
        assertThat(validator.validate(jwt("not-a-uuid", List.of("authenticated"))).hasErrors()).isTrue();
    }

    @Test
    void incorrectAudienceIsRejected() {
        assertThat(validator.validate(jwt(UUID.randomUUID().toString(), List.of("other"))).hasErrors()).isTrue();
    }

    @Test
    void organizationalRoleFromTokenIsRejected() {
        Jwt jwt = jwt(UUID.randomUUID().toString(), List.of("authenticated"), "OWNER");

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwt(String subject, List<String> audiences) {
        return jwt(subject, audiences, "authenticated");
    }

    private Jwt jwt(String subject, List<String> audiences, String role) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token-used-only-by-unit-test")
                .header("alg", "HS256")
                .issuer("https://auth.example.test/auth/v1")
                .audience(audiences)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", role);
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
