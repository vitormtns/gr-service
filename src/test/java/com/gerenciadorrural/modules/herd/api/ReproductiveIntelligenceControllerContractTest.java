package com.gerenciadorrural.modules.herd.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gerenciadorrural.modules.herd.application.ReadReproductiveIntelligence;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReproductiveIntelligenceControllerContractTest {
  @Test void enforcesExplicitParamsNoStoreAndEmptyReproductiveState() throws Exception {
    var context=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),
        UUID.randomUUID(),"VIEWER","ALL_FARMS");
    var reads=mock(ReadReproductiveIntelligence.class);
    when(reads.animal(eq(context),any())).thenReturn(new ReadReproductiveIntelligence.Result(LocalDate.of(2026,10,2),null,null,null));
    var mvc=MockMvcBuilders.standaloneSetup(new ReproductiveIntelligenceController(reads))
        .setControllerAdvice(new HerdAnimalExceptionHandler())
        .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule())))
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){
          public boolean supportsParameter(MethodParameter p){return p.hasParameterAnnotation(ResolvedTenantContext.class);}
          public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory b){return context;}
        }).build();
    String animal="/api/v1/herd/animals/"+UUID.randomUUID()+"/reproductive-intelligence";
    mvc.perform(get(animal)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.calving").isEmpty()).andExpect(jsonPath("$.postpartum").isEmpty());
    mvc.perform(get(animal+"?farmId="+UUID.randomUUID())).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/reproduction/calving-preview?serviceOn=2026-01-01&serviceOn=2026-01-02"))
        .andExpect(status().isBadRequest());
  }
}
