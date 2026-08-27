package com.gerenciadorrural.shared.security.infrastructure;

import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                optionalClaim(jwt, "email"),
                optionalClaim(jwt, "session_id"),
                optionalClaim(jwt, "aal"),
                jwt.getIssuedAt(),
                jwt.getExpiresAt()
        );
        return new AuthenticatedUserAuthenticationToken(user);
    }

    private static Optional<String> optionalClaim(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
