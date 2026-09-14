package com.gerenciadorrural.modules.herd.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gerenciadorrural.modules.herd.application.ReadHerdDashboard;
import com.gerenciadorrural.modules.herd.domain.HerdAgendaRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAgendaSource;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ActivityBucket;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ActivityTotals;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.AttentionSummary;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.HerdSnapshot;
import com.gerenciadorrural.modules.herd.domain.HerdDashboardRepository.ReproductionPipeline;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerStatus;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import com.gerenciadorrural.modules.herd.domain.PendingWorkType;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantId;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import com.gerenciadorrural.shared.tenancy.TenantTransactionalOperation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class HerdDashboardControllerContractTest {
  private final TenantContext context =
      new TenantContext(
          new TenantId(UUID.randomUUID()),
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID(),
          "OWNER",
          "ALL_FARMS");
  private HerdDashboardRepository repository;
  private HerdAgendaRepository agenda;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    repository = mock(HerdDashboardRepository.class);
    agenda = mock(HerdAgendaRepository.class);
    TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
    doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute())
        .when(transactions)
        .execute(any(), ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
    when(repository.snapshot(any(), any(), any(), any(), any()))
        .thenReturn(new HerdSnapshot(0, Map.of(), Map.of(), List.of(), 0));
    when(repository.activity(any(), any(), any(), any())).thenReturn(totals());
    when(repository.attention(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new AttentionSummary(0, 0, 0, 0, 0, 0, 0));
    when(repository.reproductionPipeline(any(), any(), any(), anyInt()))
        .thenReturn(new ReproductionPipeline(0, 0));
    when(repository.activitySeries(any(), any(), any(), any())).thenReturn(List.of());
    when(agenda.page(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of());
    ReadHerdDashboard service =
        new ReadHerdDashboard(
            transactions,
            repository,
            agenda,
            Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC),
            90,
            14,
            366);
    ObjectMapper json =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new HerdDashboardController(service))
            .setControllerAdvice(new HerdAnimalExceptionHandler())
            .setCustomArgumentResolvers(new TenantContextArgumentResolver())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .build();
  }

  @Test
  void exposesStableTypedContractsWithNoStore() throws Exception {
    when(repository.activitySeries(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new ActivityBucket(
                    LocalDate.of(2026, 9, 13), 1, 2, 3, 4, 5, 6, 7, 8)));
    UUID animal = UUID.randomUUID();
    when(agenda.page(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(
            List.of(
                new HerdAgendaRepository.Row(
                    HerdAgendaSource.MANUAL,
                    HerdPlannerType.WEIGHING.name(),
                    LocalDate.of(2026, 9, 1),
                    UUID.randomUUID().toString(),
                    animal,
                    "Pesagem programada",
                    null,
                    null,
                    UUID.randomUUID(),
                    null,
                    null,
                    HerdPlannerStatus.OPEN),
                new HerdAgendaRepository.Row(
                    HerdAgendaSource.DERIVED,
                    HerdPlannerType.VACCINATION.name(),
                    LocalDate.of(2026, 9, 2),
                    animal + ":VACCINATION_DUE",
                    animal,
                    "Vacinação pendente",
                    "A-1",
                    "Aurora",
                    null,
                    PendingWorkType.VACCINATION_DUE,
                    null,
                    null)));

    mvc.perform(get("/api/v1/herd/dashboard/overview?period=TODAY"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
        .andExpect(jsonPath("$.*", hasSize(5)))
        .andExpect(jsonPath("$.period.period").value("TODAY"))
        .andExpect(jsonPath("$.period.from").value("2026-09-13"))
        .andExpect(jsonPath("$.herdSnapshot.activeAnimals").value(0))
        .andExpect(jsonPath("$.periodActivity.births").value(0))
        .andExpect(jsonPath("$.attention.openPlannerItems").value(0))
        .andExpect(jsonPath("$.insights.weighingCoverage.type").value("WEIGHING_COVERAGE"))
        .andExpect(jsonPath("$.insights.healthDue.type").value("HEALTH_DUE"))
        .andExpect(jsonPath("$.insights.reproductionPipeline.type").value("REPRODUCTION_PIPELINE"))
        .andExpect(jsonPath("$.insights.calvingAttention.type").value("CALVING_ATTENTION"))
        .andExpect(jsonPath("$.insights.plannerExecution.type").value("PLANNER_EXECUTION"))
        .andExpect(jsonPath("$.insights.herdActivity.type").value("HERD_ACTIVITY"));

    mvc.perform(get("/api/v1/herd/dashboard/activity?period=TODAY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series", hasSize(1)))
        .andExpect(jsonPath("$.series[0].date").value("2026-09-13"))
        .andExpect(jsonPath("$.series[0].healthTreatments").value(6));

    mvc.perform(get("/api/v1/herd/dashboard/attention?previewSize=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preview", hasSize(2)))
        .andExpect(jsonPath("$.preview[0].source").value("MANUAL"))
        .andExpect(jsonPath("$.preview[1].source").value("DERIVED"))
        .andExpect(jsonPath("$.preview[1].animal.id").value(animal.toString()));
  }

  @Test
  void rejectsMalformedAmbiguousUnknownAndRepeatedParameters() throws Exception {
    List<MockHttpServletRequestBuilder> invalid =
        List.of(
            get("/api/v1/herd/dashboard/overview?period=today"),
            get("/api/v1/herd/dashboard/overview?period=CUSTOM"),
            get("/api/v1/herd/dashboard/overview?period=TODAY&from=2026-09-13"),
            get("/api/v1/herd/dashboard/overview?period=CUSTOM&from=bad&to=2026-09-13"),
            get("/api/v1/herd/dashboard/overview?period=CUSTOM&from=2026-09-14&to=2026-09-13"),
            get("/api/v1/herd/dashboard/overview?sex=female"),
            get("/api/v1/herd/dashboard/overview?category=CALF"),
            get("/api/v1/herd/dashboard/overview?paddockId=bad"),
            get("/api/v1/herd/dashboard/activity?unknown=true"),
            get("/api/v1/herd/dashboard/activity?period=TODAY&period=LAST_7_DAYS"),
            get("/api/v1/herd/dashboard/attention?previewSize=0"),
            get("/api/v1/herd/dashboard/attention?previewSize=11"));

    for (MockHttpServletRequestBuilder request : invalid) {
      mvc.perform(request)
          .andExpect(status().isBadRequest())
          .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
          .andExpect(jsonPath("$.code").value("HERD_QUERY_INVALID"));
    }
    verifyNoInteractions(repository, agenda);
  }

  private static ActivityTotals totals() {
    return new ActivityTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private final class TenantContextArgumentResolver implements HandlerMethodArgumentResolver {
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
        WebDataBinderFactory binderFactory) {
      return context;
    }
  }
}
