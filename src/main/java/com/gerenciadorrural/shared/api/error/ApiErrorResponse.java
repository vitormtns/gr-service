package com.gerenciadorrural.shared.api.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        int status,
        String requestId,
        List<ValidationError> validationErrors,
        Instant timestamp
) {

    public ApiErrorResponse {
        validationErrors = List.copyOf(validationErrors);
    }

    public record ValidationError(String field, String message) {
    }
}
