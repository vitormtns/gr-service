package com.gerenciadorrural.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component("httpRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_ATTRIBUTE = RequestContextFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = validOrGenerated(request.getHeader(REQUEST_ID_HEADER));
        String correlationId = validOrFallback(request.getHeader(CORRELATION_ID_HEADER), requestId);

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try (MDC.MDCCloseable ignoredRequest = MDC.putCloseable("requestId", requestId);
             MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable("correlationId", correlationId)) {
            filterChain.doFilter(request, response);
        }
    }

    static boolean isSafeId(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    private static String validOrGenerated(String value) {
        return isSafeId(value) ? value : UUID.randomUUID().toString();
    }

    private static String validOrFallback(String value, String fallback) {
        return isSafeId(value) ? value : fallback;
    }
}
