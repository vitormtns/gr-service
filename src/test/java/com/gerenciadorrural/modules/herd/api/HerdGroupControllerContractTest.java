package com.gerenciadorrural.modules.herd.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gerenciadorrural.modules.herd.application.HerdGroupService;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdGroup;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

class HerdGroupControllerContractTest {
  private final TenantContext context = new TenantContext(new TenantId(UUID.randomUUID()),
      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER", "ALL_FARMS");

  @Test
  void acceptsStrictGroupCommandsAndRejectsUnknownAndDuplicateFields() throws Exception {
    HerdGroupService service = mock(HerdGroupService.class);
    UUID groupId = UUID.randomUUID();
    HerdGroup.Rules rules = new HerdGroup.Rules(HerdAnimalSex.FEMALE, null, 24, null, true, false);
    HerdGroup group = new HerdGroup(groupId, "Matrizes", HerdGroup.Kind.SMART,
        HerdGroup.Status.ACTIVE, rules, 0);
    when(service.create(eq(context), eq(groupId), eq("Matrizes"), eq(HerdGroup.Kind.SMART),
        eq(rules))).thenReturn(new HerdGroupService.Result(group, true));
    MockMvc mvc = mvc(service);
    String body = "{\"id\":\"" + groupId + "\",\"name\":\"Matrizes\",\"kind\":\"SMART\","
        + "\"rules\":{\"sex\":\"FEMALE\",\"minAgeMonths\":24,\"onlyReproductionActive\":true}}";
    mvc.perform(post("/api/v1/herd/groups").contentType("application/json").content(body))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("SMART"));
    verify(service).create(context, groupId, "Matrizes", HerdGroup.Kind.SMART, rules);
    mvc.perform(post("/api/v1/herd/groups").contentType("application/json")
        .content(body.replace("\"name\"", "\"unknown\"")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/herd/groups").contentType("application/json")
        .content(body.replace("\"minAgeMonths\":24", "\"minAgeMonths\":24,\"minAgeMonths\":12")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/groups?tenantId=" + UUID.randomUUID()))
        .andExpect(status().isBadRequest());
  }

  private MockMvc mvc(HerdGroupService service) {
    ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new HerdGroupController(service))
        .setControllerAdvice(new HerdAnimalExceptionHandler())
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(ResolvedTenantContext.class)
                && parameter.getParameterType() == TenantContext.class;
          }

          @Override
          public Object resolveArgument(MethodParameter parameter,
              ModelAndViewContainer container, NativeWebRequest request,
              WebDataBinderFactory binderFactory) {
            return context;
          }
        })
        .setMessageConverters(new StringHttpMessageConverter(),
            new MappingJackson2HttpMessageConverter(json)).build();
  }
}
