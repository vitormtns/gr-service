package com.gerenciadorrural.modules.farms.api;

import com.gerenciadorrural.modules.farms.application.FarmProfileNotAvailableException;
import com.gerenciadorrural.modules.organizations.application.TenantContextNotAvailableException;
import com.gerenciadorrural.shared.api.error.ApiErrorResponse;
import com.gerenciadorrural.shared.observability.RequestContextFilter;
import com.gerenciadorrural.shared.tenancy.TenantTransactionInfrastructureException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = FarmProfileController.class)
class FarmProfileExceptionHandler {

    @ExceptionHandler({FarmProfileNotAvailableException.class, TenantContextNotAvailableException.class})
    ResponseEntity<ApiErrorResponse> unavailable(HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                "FARM_PROFILE_NOT_AVAILABLE",
                "O perfil da fazenda solicitada não está disponível",
                request
        );
    }

    @ExceptionHandler({
            DataAccessException.class,
            TransactionException.class,
            TenantTransactionInfrastructureException.class
    })
    ResponseEntity<ApiErrorResponse> persistenceUnavailable(HttpServletRequest request) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "FARM_PROFILE_PERSISTENCE_UNAVAILABLE",
                "O perfil da fazenda está temporariamente indisponível",
                request
        );
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
