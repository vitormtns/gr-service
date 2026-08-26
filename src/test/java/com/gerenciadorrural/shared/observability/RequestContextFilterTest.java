package com.gerenciadorrural.shared.observability;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextFilterTest {

    private final RequestContextFilter filter = new RequestContextFilter();

    @Test
    void shouldKeepAValidClientRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "mobile-123_abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestContextFilter.REQUEST_ID_HEADER)).isEqualTo("mobile-123_abc");
        assertThat(request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("mobile-123_abc");
    }

    @Test
    void shouldReplaceAnUnsafeRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "valor com espaços e conteúdo inseguro");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(RequestContextFilter.REQUEST_ID_HEADER);
        assertThat(generated).isNotBlank().isNotEqualTo("valor com espaços e conteúdo inseguro");
        assertThat(RequestContextFilter.isSafeId(generated)).isTrue();
    }
}
