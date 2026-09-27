package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerenciadorrural.modules.herd.application.ImportCurrentFarmAnimals;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HerdAnimalImportControllerContractTest {
    @Test
    void motherCommandRejectsUnknownAndDuplicateFields() {
        var mapper = new ObjectMapper();
        String operation = UUID.randomUUID().toString();
        assertThatThrownBy(() -> mapper.readValue("{\"operationId\":\"" + operation
                + "\",\"expectedVersion\":0,\"farmId\":\"" + UUID.randomUUID() + "\"}",
                HerdMotherController.Request.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("{\"operationId\":\"" + operation
                + "\",\"expectedVersion\":0,\"expectedVersion\":1}",
                HerdMotherController.Request.class)).isInstanceOf(Exception.class);
    }

    @Test
    void breedingBatchRejectsUnknownMotherAndDuplicateVersion() {
        var mapper = new ObjectMapper();
        String operation = UUID.randomUUID().toString();
        String mother = UUID.randomUUID().toString();
        String prefix = "{\"operationId\":\"" + operation
                + "\",\"serviceType\":\"INSEMINATION\",\"serviceOn\":\"2026-01-01\",\"mothers\":[";
        assertThatThrownBy(() -> mapper.readValue(prefix + "{\"id\":\"" + mother
                + "\",\"expectedVersion\":0,\"farmId\":\"" + UUID.randomUUID() + "\"}]}",
                HerdBreedingBatchController.Request.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue(prefix + "{\"id\":\"" + mother
                + "\",\"expectedVersion\":0,\"expectedVersion\":1}]}",
                HerdBreedingBatchController.Request.class)).isInstanceOf(Exception.class);
    }
    @Test
    void acceptsBoundedBatchAndRejectsUnknownFields() throws Exception {
        var importer = mock(ImportCurrentFarmAnimals.class);
        var context = new TenantContext(new TenantId(UUID.randomUUID()), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "OWNER", "ALL_FARMS");
        HandlerMethodArgumentResolver resolver = new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext.class);
            }
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory binder) { return context; }
        };
        MockMvc mvc = standaloneSetup(new HerdAnimalImportController(importer))
                .setControllerAdvice(new HerdAnimalExceptionHandler())
                .setCustomArgumentResolvers(resolver)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
                .build();
        UUID operation = UUID.randomUUID();
        UUID animal = UUID.randomUUID();
        String row = "{\"id\":\"" + animal + "\",\"identification\":\"A-1\",\"sex\":\"FEMALE\"}";
        String body = "{\"operationId\":\"" + operation + "\",\"animals\":[" + row + "]}";
        when(importer.execute(eq(context), eq(operation), any()))
                .thenReturn(new ImportCurrentFarmAnimals.Result(List.of(animal), false));
        mvc.perform(post("/api/v1/herd/animals/imports").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.animalIds[0]").value(animal.toString()));
        mvc.perform(post("/api/v1/herd/animals/imports").contentType("application/json")
                        .content(body.replace("\"sex\":\"FEMALE\"", "\"sex\":\"FEMALE\",\"farmId\":\"" + UUID.randomUUID() + "\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HERD_COMMAND_INVALID"));
        verify(importer, times(1)).execute(eq(context), eq(operation), any());
    }
}
