package com.gerenciadorrural.modules.platform.api;
import com.gerenciadorrural.modules.platform.application.PlatformException;
import com.gerenciadorrural.shared.api.error.ApiErrorResponse;
import com.gerenciadorrural.shared.observability.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
@RestControllerAdvice(assignableTypes=PlatformAdministrationController.class) public class PlatformExceptionHandler {
 @ExceptionHandler(PlatformException.class) ResponseEntity<ApiErrorResponse> platform(PlatformException e,HttpServletRequest r){return error(e.status(),e.code(),e.getMessage(),r);} @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiErrorResponse> invalid(IllegalArgumentException e,HttpServletRequest r){return error(400,"PLATFORM_COMMAND_INVALID",e.getMessage(),r);} @ExceptionHandler(DataAccessException.class) ResponseEntity<ApiErrorResponse> persistence(DataAccessException e,HttpServletRequest r){return error(503,"PLATFORM_PERSISTENCE_UNAVAILABLE","ServiÃ§o administrativo temporariamente indisponÃ­vel",r);} private ResponseEntity<ApiErrorResponse> error(int s,String c,String m,HttpServletRequest r){return ResponseEntity.status(s).body(new ApiErrorResponse(c,m,s,(String)r.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE),List.of(),Instant.now()));}
}
