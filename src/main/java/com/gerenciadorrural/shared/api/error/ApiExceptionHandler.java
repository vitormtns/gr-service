package com.gerenciadorrural.shared.api.error;

import com.gerenciadorrural.shared.observability.RequestContextFilter;
import com.gerenciadorrural.shared.tenancy.TenantTransactionInfrastructureException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.ValidationError> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorResponse.ValidationError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiErrorResponse.ValidationError::field))
                .toList();

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiErrorResponse response = new ApiErrorResponse(
                "validation_error",
                "Existem campos inválidos na solicitação",
                status.value(),
                requestId(request),
                details,
                Instant.now()
        );
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "request_invalid",
                "A solicitação possui dados inválidos", List.of(), request);
    }

    @ExceptionHandler({
            DataAccessException.class,
            TransactionException.class,
            TenantTransactionInfrastructureException.class
    })
    ResponseEntity<ApiErrorResponse> handlePersistenceUnavailable(Exception exception, HttpServletRequest request) {
        logFailure("Dependência de persistência indisponível", exception, request);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "persistence_unavailable",
                "O serviço está temporariamente indisponível", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleResourceNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "resource_not_found",
                "O recurso solicitado n\u00e3o foi encontrado", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        logFailure("Falha inesperada ao processar a requisição", exception, request);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Não foi possível concluir a solicitação", List.of(), request);
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.ValidationError> validationErrors,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiErrorResponse(
                code, message, status.value(), requestId(request), validationErrors, Instant.now()));
    }

    private static void logFailure(String summary, Exception exception, HttpServletRequest request) {
        LOGGER.error("{}: método={} caminho={} tipo={}", summary, request.getMethod(),
                request.getRequestURI(), exception.getClass().getName());
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? null : requestId.toString();
    }
}
