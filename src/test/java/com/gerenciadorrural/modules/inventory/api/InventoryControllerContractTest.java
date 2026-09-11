package com.gerenciadorrural.modules.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gerenciadorrural.modules.inventory.application.InventoryService;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.*;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InventoryControllerContractTest {
 private MockMvc mvc; private InventoryService service;
 @BeforeEach void setUp(){service=mock(InventoryService.class);TenantContext context=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"OWNER","ALL_FARMS");mvc=standaloneSetup(new InventoryController(service)).setControllerAdvice(new InventoryExceptionHandler()).setCustomArgumentResolvers(new Resolver(context)).setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule()))).build();}
 @Test void rejectsUnknownDuplicateMalformedAndServerOwnedMovementFields()throws Exception{String valid="{\"operationId\":\""+UUID.randomUUID()+"\",\"type\":\"RECEIPT\",\"productId\":\""+UUID.randomUUID()+"\",\"destinationLocationId\":\""+UUID.randomUUID()+"\",\"quantity\":1,\"occurredOn\":\"2026-09-10\"}";for(String body:new String[]{valid.substring(0,valid.length()-1)+",\"tenantId\":\"x\"}",valid.substring(0,valid.length()-1)+",\"quantity\":1}",valid.replace("\"productId\":\"","\"productID\":\""),valid.replace("\"quantity\":1","\"quantity\":1.0000001"),valid.replace("\"operationId\":\"","\"operationId\":\"invalid")})mvc.perform(post("/api/v1/inventory/movements").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void rejectsPatchDecimalOverflowAndUnknownFields()throws Exception{for(String body:new String[]{"{\"expectedVersion\":1.1}","{\"expectedVersion\":9223372036854775808}","{\"expectedVersion\":0,\"farmId\":\"x\"}"})mvc.perform(patch("/api/v1/inventory/products/"+UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 private record Resolver(TenantContext context) implements HandlerMethodArgumentResolver {public boolean supportsParameter(MethodParameter p){return p.hasParameterAnnotation(ResolvedTenantContext.class)&&p.getParameterType()==TenantContext.class;}public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory b){return context;}}
}
