package com.gerenciadorrural.modules.finance.api;

import com.gerenciadorrural.modules.finance.application.FinanceService.*;
import com.gerenciadorrural.shared.api.error.ApiErrorResponse;
import com.gerenciadorrural.shared.observability.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice(assignableTypes = FinanceController.class)
class FinanceExceptionHandler {
    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiErrorResponse(
                code, "A operação financeira não pôde ser concluída", status.value(),
                requestId == null ? null : requestId.toString(), List.of(), Instant.now()));
    }

    @ExceptionHandler(Invalid.class) ResponseEntity<ApiErrorResponse> invalid(HttpServletRequest r) { return error(HttpStatus.BAD_REQUEST, "FINANCE_COMMAND_INVALID", r); }
    @ExceptionHandler(Forbidden.class) ResponseEntity<ApiErrorResponse> forbidden(HttpServletRequest r) { return error(HttpStatus.FORBIDDEN, "FINANCE_FORBIDDEN", r); }
    @ExceptionHandler(CategoryNotFound.class) ResponseEntity<ApiErrorResponse> category(HttpServletRequest r) { return error(HttpStatus.NOT_FOUND, "FINANCE_CATEGORY_NOT_FOUND", r); }
    @ExceptionHandler(EntryNotFound.class) ResponseEntity<ApiErrorResponse> entry(HttpServletRequest r) { return error(HttpStatus.NOT_FOUND, "FINANCE_ENTRY_NOT_FOUND", r); }
    @ExceptionHandler(Idempotency.class) ResponseEntity<ApiErrorResponse> idempotency(HttpServletRequest r) { return error(HttpStatus.CONFLICT, "FINANCE_OPERATION_IDEMPOTENCY_CONFLICT", r); }
    @ExceptionHandler(Conflict.class) ResponseEntity<ApiErrorResponse> conflict(HttpServletRequest r) { return error(HttpStatus.CONFLICT, "FINANCE_ENTRY_CONFLICT", r); }
    @ExceptionHandler({DataAccessException.class, IllegalStateException.class}) ResponseEntity<ApiErrorResponse> persistence(HttpServletRequest r) { return error(HttpStatus.SERVICE_UNAVAILABLE, "FINANCE_PERSISTENCE_UNAVAILABLE", r); }
}
