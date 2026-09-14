package com.gerenciadorrural.shared.api.error;

import com.gerenciadorrural.shared.observability.RequestContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void sanitizesPersistenceAndUnexpectedFailures() {
        MockHttpServletRequest request = request("contract-test");

        var unavailable = handler.handlePersistenceUnavailable(
                new DataAccessResourceFailureException("jdbc:postgresql://secret-host senha=segredo"), request);
        var unexpected = handler.handleUnexpected(
                new IllegalStateException("token=segredo-interno"), request);

        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getBody()).isNotNull();
        assertThat(unavailable.getBody().code()).isEqualTo("persistence_unavailable");
        assertThat(unavailable.getBody().message()).doesNotContain("secret-host", "segredo");
        assertThat(unexpected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(unexpected.getBody()).isNotNull();
        assertThat(unexpected.getBody().code()).isEqualTo("internal_error");
        assertThat(unexpected.getBody().message()).doesNotContain("token", "segredo");
    }

    private static MockHttpServletRequest request(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/teste");
        request.setAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }
}
