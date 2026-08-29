package com.gerenciadorrural.modules.organizations.api;

import com.gerenciadorrural.modules.organizations.application.ResolveTenantContext;
import com.gerenciadorrural.modules.organizations.application.TenantContextNotAvailableException;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContextRequestAttribute;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ResolvedTenantContextArgumentResolverTest {

    @Test
    void resolvesOnceAndReusesTheSameContextInstanceWithinTheRequest() throws Exception {
        ResolveTenantContext useCase = mock(ResolveTenantContext.class);
        ResolvedTenantContextArgumentResolver resolver = new ResolvedTenantContextArgumentResolver(useCase);
        TenantContext context = context();
        ResolveTenantContext.Resolved resolved = new ResolveTenantContext.Resolved(context, "Organização", "Fazenda");
        MockHttpServletRequest request = request(context.tenantId().value(), context.farmId());
        when(useCase.execute(context.tenantId().value(), context.farmId())).thenReturn(resolved);

        Object first = resolver.resolveArgument(parameter(), null, new ServletWebRequest(request), null);
        Object second = resolver.resolveArgument(parameter(), null, new ServletWebRequest(request), null);

        assertThat(first).isSameAs(context);
        assertThat(second).isSameAs(context);
        assertThat(request.getAttribute(TenantContextRequestAttribute.NAME)).isSameAs(context);
        assertThat(request.getAttribute(ResolvedTenantContextArgumentResolver.RESOLVED_ATTRIBUTE)).isSameAs(resolved);
        verify(useCase).execute(context.tenantId().value(), context.farmId());
        verifyNoMoreInteractions(useCase);
    }

    @Test
    void invalidUuidIsAHeaderErrorAndDoesNotLeaveRequestAttributes() throws Exception {
        ResolveTenantContext useCase = mock(ResolveTenantContext.class);
        ResolvedTenantContextArgumentResolver resolver = new ResolvedTenantContextArgumentResolver(useCase);
        MockHttpServletRequest request = request("inválido", UUID.randomUUID().toString());

        assertThatThrownBy(() -> resolver.resolveArgument(
                parameter(), null, new ServletWebRequest(request), null
        )).isInstanceOfSatisfying(TenantContextHeaderException.class,
                exception -> assertThat(exception.code).isEqualTo("TENANT_CONTEXT_HEADER_INVALID"));

        assertThat(request.getAttribute(TenantContextRequestAttribute.NAME)).isNull();
        assertThat(request.getAttribute(ResolvedTenantContextArgumentResolver.RESOLVED_ATTRIBUTE)).isNull();
        verifyNoMoreInteractions(useCase);
    }

    @Test
    void applicationFailureIsNotMisclassifiedAsAnInvalidHeaderAndLeavesNoContext() throws Exception {
        ResolveTenantContext useCase = mock(ResolveTenantContext.class);
        ResolvedTenantContextArgumentResolver resolver = new ResolvedTenantContextArgumentResolver(useCase);
        UUID organizationId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        MockHttpServletRequest request = request(organizationId, farmId);
        TenantContextNotAvailableException failure = new TenantContextNotAvailableException();
        when(useCase.execute(organizationId, farmId)).thenThrow(failure);

        assertThatThrownBy(() -> resolver.resolveArgument(
                parameter(), null, new ServletWebRequest(request), null
        )).isSameAs(failure);

        assertThat(request.getAttribute(TenantContextRequestAttribute.NAME)).isNull();
        assertThat(request.getAttribute(ResolvedTenantContextArgumentResolver.RESOLVED_ATTRIBUTE)).isNull();
    }

    private static MockHttpServletRequest request(UUID organizationId, UUID farmId) {
        return request(organizationId.toString(), farmId.toString());
    }

    private static MockHttpServletRequest request(String organizationId, String farmId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Organization-Id", organizationId);
        request.addHeader("X-Farm-Id", farmId);
        return request;
    }

    private static MethodParameter parameter() throws NoSuchMethodException {
        Method method = ParameterHolder.class.getDeclaredMethod("endpoint", TenantContext.class);
        return new MethodParameter(method, 0);
    }

    private static TenantContext context() {
        return new TenantContext(
                new TenantId(UUID.randomUUID()),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "OWNER",
                "ALL_FARMS"
        );
    }

    @SuppressWarnings("unused")
    private static final class ParameterHolder {
        void endpoint(@ResolvedTenantContext TenantContext context) {
        }
    }
}
