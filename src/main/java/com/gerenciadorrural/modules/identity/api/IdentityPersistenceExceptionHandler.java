package com.gerenciadorrural.modules.identity.api;

import com.gerenciadorrural.modules.organizations.api.CurrentUserOrganizationsController;
import com.gerenciadorrural.shared.api.error.ApiErrorResponse;
import com.gerenciadorrural.shared.observability.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {CurrentUserController.class, CurrentUserOrganizationsController.class})
class IdentityPersistenceExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrorResponse> handlePersistenceUnavailable(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE);
        ApiErrorResponse response = new ApiErrorResponse(
                "IDENTITY_PERSISTENCE_UNAVAILABLE",
                "O servi\u00e7o de identidade est\u00e1 temporariamente indispon\u00edvel",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                requestId == null ? null : requestId.toString(),
                List.of(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
