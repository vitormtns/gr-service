package com.gerenciadorrural.modules.organizations.api;
import com.gerenciadorrural.modules.organizations.application.ResolveTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContextRequestAttribute;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import java.util.Objects;
import java.util.UUID;
@Component public class ResolvedTenantContextArgumentResolver implements HandlerMethodArgumentResolver {
 static final String RESOLVED_ATTRIBUTE = ResolvedTenantContextArgumentResolver.class.getName() + ".resolved";
 private final ResolveTenantContext resolver; public ResolvedTenantContextArgumentResolver(ResolveTenantContext resolver){this.resolver=resolver;}
 public boolean supportsParameter(MethodParameter p){return p.hasParameterAnnotation(ResolvedTenantContext.class)&&p.getParameterType().equals(TenantContext.class);}
 public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest request,WebDataBinderFactory b){
  HttpServletRequest servlet=Objects.requireNonNull(request.getNativeRequest(HttpServletRequest.class));
  Object existingContext=servlet.getAttribute(TenantContextRequestAttribute.NAME);
  Object existingResolved=servlet.getAttribute(RESOLVED_ATTRIBUTE);
  if(existingContext instanceof TenantContext context&&existingResolved instanceof ResolveTenantContext.Resolved resolved&&resolved.context()==context)return context;
  String org=request.getHeader("X-Organization-Id"),farm=request.getHeader("X-Farm-Id");
  if(org==null||farm==null)throw new TenantContextHeaderException("TENANT_CONTEXT_HEADER_MISSING");
  UUID organizationId=parse(org),farmId=parse(farm);
  var result=resolver.execute(organizationId,farmId);
  servlet.setAttribute(TenantContextRequestAttribute.NAME,result.context());
  servlet.setAttribute(RESOLVED_ATTRIBUTE,result);
  return result.context();
 }
 private static UUID parse(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException exception){throw new TenantContextHeaderException("TENANT_CONTEXT_HEADER_INVALID");}}
}
