package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gerenciadorrural.modules.herd.application.ListCurrentFarmAnimals;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalPage;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalQuery;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HerdAnimalControllerContractTest {

    private final TenantContext tenantContext = new TenantContext(
        new TenantId(UUID.randomUUID()),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "OWNER",
        "ALL_FARMS"
    );

    private ListCurrentFarmAnimals listCurrentFarmAnimals;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        listCurrentFarmAnimals = mock(ListCurrentFarmAnimals.class);
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = standaloneSetup(new HerdAnimalController(listCurrentFarmAnimals))
            .setControllerAdvice(new HerdAnimalExceptionHandler())
            .setCustomArgumentResolvers(new TenantContextArgumentResolver())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void rejectsInvalidQueryParametersWithoutCallingTheUseCase() throws Exception {
        List<MockHttpServletRequestBuilder> invalidRequests = List.of(
            get("/api/v1/herd/animals").queryParam("page", "-1"),
            get("/api/v1/herd/animals").queryParam("size", "0"),
            get("/api/v1/herd/animals").queryParam("size", "-1"),
            get("/api/v1/herd/animals").queryParam("size", "101"),
            get("/api/v1/herd/animals").queryParam("sex", "INVALID"),
            get("/api/v1/herd/animals").queryParam("status", "INVALID"),
            get("/api/v1/herd/animals").queryParam("page", "zero"),
            get("/api/v1/herd/animals").queryParam("size", "fifty"),
            get("/api/v1/herd/animals").queryParam("unexpected", "value"),
            get("/api/v1/herd/animals").queryParam("search", "A", "B")
        );

        for (MockHttpServletRequestBuilder request : invalidRequests) {
            mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(
                    header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))
                )
                .andExpect(jsonPath("$.code").value("HERD_QUERY_INVALID"))
                .andExpect(content().string(not(containsString("HerdAnimalQueryException"))))
                .andExpect(content().string(not(containsString("java.lang"))));
        }

        verifyNoInteractions(listCurrentFarmAnimals);
    }

    @Test
    void appliesDefaultsAndNormalizesSearchWithoutChangingInternalWhitespace()
        throws Exception {
        List<HerdAnimalQuery> queries = recordQueries();

        mvc.perform(get("/api/v1/herd/animals")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/herd/animals").queryParam("search", ""))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/herd/animals").queryParam("search", "   "))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/herd/animals").queryParam("search", "  água  doce  "))
            .andExpect(status().isOk());

        assertThat(queries).containsExactly(
            new HerdAnimalQuery(null, null, null, 0, 50),
            new HerdAnimalQuery(null, null, null, 0, 50),
            new HerdAnimalQuery(null, null, null, 0, 50),
            new HerdAnimalQuery("água  doce", null, null, 0, 50)
        );
    }

    @Test
    void forwardsAllowedSexAndStatusValues() throws Exception {
        List<HerdAnimalQuery> queries = recordQueries();

        mvc.perform(get("/api/v1/herd/animals").queryParam("sex", "MALE"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/herd/animals").queryParam("sex", "FEMALE"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/herd/animals").queryParam("status", "ACTIVE"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/herd/animals").queryParam("status", "SOLD"))
            .andExpect(status().isOk());

        assertThat(queries).containsExactly(
            new HerdAnimalQuery(null, HerdAnimalSex.MALE, null, 0, 50),
            new HerdAnimalQuery(null, HerdAnimalSex.FEMALE, null, 0, 50),
            new HerdAnimalQuery(null, null, HerdAnimalStatus.ACTIVE, 0, 50),
            new HerdAnimalQuery(null, null, HerdAnimalStatus.SOLD, 0, 50)
        );
    }

    @Test
    void returnsOnlyThePublicAnimalListingContract() throws Exception {
        HerdAnimalSummary animal = new HerdAnimalSummary(
            UUID.randomUUID(),
            "A-001",
            "Brisa",
            HerdAnimalSex.FEMALE,
            LocalDate.of(2024, 3, 15),
            HerdAnimalStatus.ACTIVE,
            3
        );
        when(listCurrentFarmAnimals.execute(any(), any()))
            .thenReturn(new HerdAnimalPage(List.of(animal), 1, 10, 11));

        mvc.perform(get("/api/v1/herd/animals").queryParam("page", "1").queryParam("size", "10"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(jsonPath("$.*", hasSize(5)))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(11))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].*", hasSize(7)))
            .andExpect(jsonPath("$.items[0].id").value(animal.id().toString()))
            .andExpect(jsonPath("$.items[0].identification").value("A-001"))
            .andExpect(jsonPath("$.items[0].name").value("Brisa"))
            .andExpect(jsonPath("$.items[0].sex").value("FEMALE"))
            .andExpect(jsonPath("$.items[0].birthDate").value("2024-03-15"))
            .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].version").value(3))
            .andExpect(jsonPath("$.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.items[0].farmId").doesNotExist())
            .andExpect(jsonPath("$.items[0].createdAt").doesNotExist())
            .andExpect(jsonPath("$.items[0].updatedAt").doesNotExist())
            .andExpect(jsonPath("$.items[0].userId").doesNotExist())
            .andExpect(jsonPath("$.items[0].membershipId").doesNotExist())
            .andExpect(jsonPath("$.items[0].token").doesNotExist());
    }

    private List<HerdAnimalQuery> recordQueries() {
        List<HerdAnimalQuery> queries = new ArrayList<>();
        when(listCurrentFarmAnimals.execute(any(), any())).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(1));
            HerdAnimalQuery query = invocation.getArgument(1);
            return new HerdAnimalPage(List.of(), query.page(), query.size(), 0);
        });
        return queries;
    }

    private final class TenantContextArgumentResolver
        implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(ResolvedTenantContext.class)
                && parameter.getParameterType().equals(TenantContext.class);
        }

        @Override
        public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binderFactory
        ) {
            return tenantContext;
        }
    }
}
