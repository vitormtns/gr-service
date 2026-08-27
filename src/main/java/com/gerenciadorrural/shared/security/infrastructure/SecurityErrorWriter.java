package com.gerenciadorrural.shared.security.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.shared.api.error.ApiErrorResponse;
import com.gerenciadorrural.shared.observability.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
final class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String requestId = (String) request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE);
        ApiErrorResponse body = new ApiErrorResponse(code, message, status, requestId, List.of(), Instant.now());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
