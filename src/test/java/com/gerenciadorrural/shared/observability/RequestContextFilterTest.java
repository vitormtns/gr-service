package com.gerenciadorrural.shared.observability;

import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void shouldReturnCorrelationIdAndClearEveryMdcField() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "request-123");
        request.addHeader(RequestContextFilter.CORRELATION_ID_HEADER, "correlation-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestInside = new AtomicReference<>();
        AtomicReference<String> correlationInside = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            requestInside.set(MDC.get("requestId"));
            correlationInside.set(MDC.get("correlationId"));
            MDC.put("tenantId", "tenant-temporário");
        };

        filter.doFilter(request, response, chain);

        assertThat(requestInside).hasValue("request-123");
        assertThat(correlationInside).hasValue("correlation-456");
        assertThat(response.getHeader(RequestContextFilter.CORRELATION_ID_HEADER))
                .isEqualTo("correlation-456");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void shouldReplaceUnsafeCorrelationIdWithRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "request-seguro");
        request.addHeader(RequestContextFilter.CORRELATION_ID_HEADER, "valor inseguro");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestContextFilter.CORRELATION_ID_HEADER))
                .isEqualTo("request-seguro");
    }
}
