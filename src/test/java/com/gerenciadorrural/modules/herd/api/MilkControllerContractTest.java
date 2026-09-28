package com.gerenciadorrural.modules.herd.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gerenciadorrural.modules.herd.application.MilkService;
import com.gerenciadorrural.modules.herd.domain.MilkRecordRepository.MilkRecord;
import com.gerenciadorrural.modules.herd.domain.MilkSession;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

class MilkControllerContractTest {
  private final TenantContext context = new TenantContext(new TenantId(UUID.randomUUID()),
      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER", "ALL_FARMS");

  @Test
  void acceptsMilkCommandAndRejectsUnknownFieldsAndQueryParameters() throws Exception {
    MilkService milk = mock(MilkService.class);
    UUID animal = UUID.randomUUID();
    UUID operation = UUID.randomUUID();
    when(milk.record(eq(context), eq(animal), any())).thenReturn(new MilkRecord(
        UUID.randomUUID(), operation, animal, LocalDate.of(2026, 9, 27),
        new BigDecimal("12.500"), MilkSession.MORNING, null, context.userId(), Instant.now()));
    MockMvc mvc = mvc(milk);
    String body = "{\"operationId\":\"" + operation
        + "\",\"expectedVersion\":0,\"recordedOn\":\"2026-09-27\","
        + "\"liters\":12.500,\"session\":\"MORNING\"}";
    mvc.perform(post("/api/v1/herd/animals/{id}/milk-records", animal)
            .contentType("application/json").content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.liters").value(12.5));
    verify(milk).record(eq(context), eq(animal), eq(new MilkService.Command(operation, 0L,
        LocalDate.of(2026, 9, 27), new BigDecimal("12.500"), MilkSession.MORNING, null)));
    mvc.perform(post("/api/v1/herd/animals/{id}/milk-records", animal)
            .contentType("application/json").content(body.replace("session", "unknown")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/herd/animals/{id}/milk-records", animal)
            .contentType("application/json")
            .content(body.replace("\"liters\":12.500", "\"liters\":12.500,\"liters\":1")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/herd/animals/{id}/milk-records", animal)
            .contentType("application/json")
            .content(body.replace("\"expectedVersion\":0,", "")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/milk/overview?unknown=true"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/animals/{id}/milk-summary?referenceDate=2026-09-27", animal))
        .andExpect(status().isOk());
    verify(milk).animalSummary(context, animal, LocalDate.of(2026, 9, 27));
  }

  private MockMvc mvc(MilkService milk) {
    ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new MilkController(milk))
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
        .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
  }
}
