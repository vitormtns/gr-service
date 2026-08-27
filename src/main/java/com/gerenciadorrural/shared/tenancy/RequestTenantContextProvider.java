package com.gerenciadorrural.shared.tenancy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RequestTenantContextProvider implements TenantContextProvider {
    private final HttpServletRequest request;
    public RequestTenantContextProvider(HttpServletRequest request) { this.request = request; }
    @Override public Optional<TenantContext> currentContext() {
        Object value = request.getAttribute(TenantContextRequestAttribute.NAME);
        return value instanceof TenantContext context ? Optional.of(context) : Optional.empty();
    }
}
