package com.gerenciadorrural.shared.security.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCorsTest {

    @Test
    void groupMembershipPutPreflightAllowsConfiguredOriginAndTenantHeaders() throws Exception {
        var response = preflight("http://localhost:4200", "PUT");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4200");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("PUT");
        assertThat(response.getHeader("Access-Control-Allow-Headers"))
                .contains("Authorization", "Content-Type", "X-Organization-Id", "X-Farm-Id");
    }

    @Test
    void groupMembershipPutPreflightRejectsUnconfiguredOrigin() throws Exception {
        assertThat(preflight("https://untrusted.example", "PUT").getStatus()).isEqualTo(403);
    }

    @Test
    void preflightStillRejectsUnsupportedMethod() throws Exception {
        assertThat(preflight("http://localhost:4200", "TRACE").getStatus()).isEqualTo(403);
    }

    private MockHttpServletResponse preflight(String origin, String method) throws Exception {
        var source = new SecurityConfiguration().corsConfigurationSource(new HttpSecurityProperties(
                new HttpSecurityProperties.Cors(List.of("http://localhost:4200"), false, Duration.ofMinutes(30))));
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/herd/groups/group/animals/animal");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", method);
        request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type, X-Organization-Id, X-Farm-Id");
        var response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}
