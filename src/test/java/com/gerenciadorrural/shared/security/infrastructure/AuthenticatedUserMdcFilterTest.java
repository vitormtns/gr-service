package com.gerenciadorrural.shared.security.infrastructure;

import com.gerenciadorrural.shared.security.model.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserMdcFilterTest {

    @AfterEach
    void cleanContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void userIdExistsOnlyDuringAuthenticatedRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                userId, Optional.empty(), Optional.empty(), Optional.empty(),
                Instant.now(), Instant.now().plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new AuthenticatedUserAuthenticationToken(user));
        AtomicReference<String> valueInsideChain = new AtomicReference<>();
        FilterChain chain = (request, response) -> valueInsideChain.set(MDC.get("userId"));

        new AuthenticatedUserMdcFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(valueInsideChain).hasValue(userId.toString());
        assertThat(MDC.get("userId")).isNull();
    }
}
