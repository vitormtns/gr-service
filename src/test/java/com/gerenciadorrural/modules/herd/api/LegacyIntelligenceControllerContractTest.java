package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LegacyIntelligenceControllerContractTest {
  private final TenantContext context=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),
      UUID.randomUUID(),UUID.randomUUID(),"OWNER","ALL_FARMS");
  @Test void ageUsesContractedHorizonAndRejectsAlternativeOrRepeatedScope() throws Exception {
    var service=mock(ReadHerdAgeIntelligence.class);
    when(service.transitions(context,15,0,20)).thenReturn(new ReadHerdAgeIntelligence.Page(
        LocalDate.of(2026,10,2),15,List.of(),0,20,0));
    var mvc=mvc(new HerdAgeIntelligenceController(service));
    mvc.perform(get("/api/v1/herd/age-intelligence/transitions"))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.horizonDays").value(15)).andExpect(jsonPath("$.referenceDate").value("2026-10-02"));
    mvc.perform(get("/api/v1/herd/age-intelligence/transitions?farmId="+UUID.randomUUID())).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/age-intelligence/transitions?horizonDays=15&horizonDays=16")).andExpect(status().isBadRequest());
    verify(service,times(1)).transitions(any(),anyInt(),anyInt(),anyInt());
  }
  @Test void groupSelectionCommandsAreStrictAndReplayHasNoStore() throws Exception {
    var service=mock(ManageGroupSelection.class);
    var id=UUID.randomUUID();var animal=UUID.randomUUID();var operation=UUID.randomUUID();
    var group=new HerdGroup(id,"Lote",HerdGroup.Kind.MANUAL,HerdGroup.Status.ACTIVE,HerdGroup.Rules.empty(),1);
    when(service.create(context,operation,id,"Lote",List.of(animal)))
        .thenReturn(new ManageGroupSelection.Result(group,1,0,false))
        .thenReturn(new ManageGroupSelection.Result(group,1,0,true));
    var mvc=mvc(new GroupSelectionController(service));
    String body="{\"id\":\""+id+"\",\"operationId\":\""+operation+"\",\"name\":\"Lote\",\"animalIds\":[\""+animal+"\"]}";
    mvc.perform(post("/api/v1/herd/groups/with-animals").contentType("application/json").content(body))
        .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"));
    mvc.perform(post("/api/v1/herd/groups/with-animals").contentType("application/json").content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
    for(String invalid:List.of(body.replace("\"name\":\"Lote\"","\"name\":\"Lote\",\"name\":\"Outro\""),
        body.substring(0,body.length()-1)+",\"tenantId\":\""+UUID.randomUUID()+"\"}",body+"{}",
        body.replace("[\""+animal+"\"]","[1]"))) {
      mvc.perform(post("/api/v1/herd/groups/with-animals").contentType("application/json").content(invalid))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(post("/api/v1/herd/groups/with-animals?farmId="+UUID.randomUUID()).contentType("application/json").content(body))
        .andExpect(status().isBadRequest());
    verify(service,times(2)).create(any(),any(),any(),anyString(),anyList());
  }
  private MockMvc mvc(Object controller) {
    return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new HerdAnimalExceptionHandler())
        .setMessageConverters(new StringHttpMessageConverter(),new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){
          public boolean supportsParameter(MethodParameter p){return p.hasParameterAnnotation(ResolvedTenantContext.class);}
          public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory b){return context;}
        }).build();
  }
}
