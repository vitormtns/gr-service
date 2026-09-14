package com.gerenciadorrural.modules.identity.api;

import com.gerenciadorrural.modules.identity.application.InternalUserConflictException;
import com.gerenciadorrural.modules.identity.application.InternalUserDeactivatedException;
import com.gerenciadorrural.modules.identity.application.InternalUserSuspendedException;
import com.gerenciadorrural.shared.api.error.ApiErrorResponse;
import com.gerenciadorrural.shared.observability.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(InternalUserSuspendedException.class)
    ResponseEntity<ApiErrorResponse> handleSuspendedUser(HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "INTERNAL_USER_SUSPENDED",
                "O acesso deste usu\u00e1rio est\u00e1 suspenso", request);
    }

    @ExceptionHandler(InternalUserDeactivatedException.class)
    ResponseEntity<ApiErrorResponse> handleDeactivatedUser(HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "INTERNAL_USER_DEACTIVATED",
                "O acesso deste usu\u00e1rio est\u00e1 desativado", request);
    }

    @ExceptionHandler(InternalUserConflictException.class)
    ResponseEntity<ApiErrorResponse> handleIdentityConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INTERNAL_USER_CONFLICT",
                "A identidade foi alterada simultaneamente; tente novamente", request);
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        Object requestId = request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE);
        ApiErrorResponse response = new ApiErrorResponse(
                code,
                message,
                status.value(),
                requestId == null ? null : requestId.toString(),
                List.of(),
                Instant.now()
        );
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }
}
