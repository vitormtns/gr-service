package com.gerenciadorrural.shared.security.infrastructure;

import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

final class AuthenticatedUserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;

    AuthenticatedUserAuthenticationToken(AuthenticatedUser principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }
}
