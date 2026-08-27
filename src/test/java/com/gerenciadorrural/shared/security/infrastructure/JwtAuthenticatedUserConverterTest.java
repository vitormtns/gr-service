package com.gerenciadorrural.shared.security.infrastructure;

import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticatedUserConverterTest {

    private final JwtAuthenticatedUserConverter converter = new JwtAuthenticatedUserConverter();

    @Test
    void optionalClaimsAreMappedWithoutCarryingRawToken() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId)
                .claim("email", "pessoa@example.test")
                .claim("session_id", UUID.randomUUID().toString())
                .claim("aal", "aal1")
                .claim("organization_role", "OWNER")
                .build();

        AbstractAuthenticationToken authentication = converter.convert(jwt);
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.email()).contains("pessoa@example.test");
        assertThat(user.sessionId()).isPresent();
        assertThat(user.authenticationLevel()).contains("aal1");
        assertThat(user.toString()).doesNotContain("raw-access-token");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_AUTHENTICATED")
                .doesNotContain("ROLE_OWNER");
    }

    @Test
    void absentOptionalClaimsBecomeEmptyValues() {
        AuthenticatedUser user = (AuthenticatedUser) converter.convert(jwt(UUID.randomUUID()).build()).getPrincipal();

        assertThat(user.email()).isEmpty();
        assertThat(user.sessionId()).isEmpty();
        assertThat(user.authenticationLevel()).isEmpty();
    }

    private Jwt.Builder jwt(UUID userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("raw-access-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuer("https://auth.example.test/auth/v1")
                .audience(List.of("authenticated"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "authenticated");
    }
}
