package com.gerenciadorrural.modules.herd.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gerenciadorrural.modules.herd.application.ReadHerdReports;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.modules.herd.domain.HerdReportRepository.*;
import com.gerenciadorrural.modules.organizations.api.ResolvedTenantContext;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class HerdReportControllerContractTest {
  private final TenantContext context =
      new TenantContext(
          new TenantId(UUID.randomUUID()),
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID(),
          "OWNER",
          "ALL_FARMS");

  private HerdReportRepository repository;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    repository = mock(HerdReportRepository.class);
    TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
    doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute())
        .when(transactions)
        .execute(any(), ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
    ReadHerdReports reports =
        new ReadHerdReports(
            transactions,
            repository,
            Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC),
            365,
            3650);
    ObjectMapper json =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new HerdReportController(reports))
            .setControllerAdvice(new HerdAnimalExceptionHandler())
            .setCustomArgumentResolvers(new TenantContextArgumentResolver())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .build();
  }

  @Test
  void exposesCurrentBalanceWithoutClaimingHistoricalOrOfficialState() throws Exception {
    when(repository.currentAgeSexCounts(any(), any())).thenReturn(List.of(
        new AgeSexCount(HerdAnimalSex.FEMALE, LocalDate.of(2026, 6, 13), 2),
        new AgeSexCount(HerdAnimalSex.MALE, null, 1),
        new AgeSexCount(HerdAnimalSex.MALE, LocalDate.of(2024, 1, 1), 3)));

    mvc.perform(get("/api/v1/herd/reports/current-age-sex-balance?referenceDate=2026-09-13"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
        .andExpect(jsonPath("$.positionSemantics").value("CURRENT_STATE_AGED_AT_REFERENCE"))
        .andExpect(jsonPath("$.totalActiveAnimals").value(6))
        .andExpect(jsonPath("$.unknownBirthDate").value(1))
        .andExpect(jsonPath("$.cells[3].ageBand").value("MONTHS_3_8"))
        .andExpect(jsonPath("$.cells[3].sex").value("FEMALE"))
        .andExpect(jsonPath("$.cells[3].count").value(2));

    mvc.perform(get("/api/v1/herd/reports/current-age-sex-balance?referenceDate=2026-09-14"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/reports/current-age-sex-balance?unknown=true"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exposesSignedAgeBandChangesAndHistoricalEventAges() throws Exception {
    LocalDate from=LocalDate.of(2026,9,1),to=LocalDate.of(2026,9,13),birth=LocalDate.of(2026,6,10);
    when(repository.historicalAgeSexCounts(context.tenantId(),context.farmId(),from.minusDays(1)))
        .thenReturn(List.of(new AgeSexCount(HerdAnimalSex.FEMALE,birth,1)));
    when(repository.historicalAgeSexCounts(context.tenantId(),context.farmId(),to))
        .thenReturn(List.of(new AgeSexCount(HerdAnimalSex.FEMALE,birth,1)));
    when(repository.ageSexFlows(context.tenantId(),context.farmId(),from,to)).thenReturn(List.of());
    mvc.perform(get("/api/v1/herd/reports/age-sex-period?from=2026-09-01&to=2026-09-13"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.openingOn").value("2026-08-31"))
        .andExpect(jsonPath("$.cells[1].ageBandChange").value(-1))
        .andExpect(jsonPath("$.cells[3].ageBandChange").value(1))
        .andExpect(jsonPath("$.totals.ageBandChange").value(0));
    mvc.perform(get("/api/v1/herd/reports/age-sex-period?from=2026-09-14&to=2026-09-13"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void derivesDrilldownBirthBoundsFromExplicitReferenceAndRejectsAmbiguousFilters() throws Exception {
    when(repository.ageSexAnimals(any(),any(),any(),anyBoolean(),any(),any(),any(),anyBoolean(),anyInt(),anyLong()))
        .thenReturn(new AgeSexAnimals(List.of(),0));
    mvc.perform(get("/api/v1/herd/reports/historical-age-sex-animals?asOf=2026-02-28&ageBand=MONTHS_0_2&sex=FEMALE"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.referenceDate").value("2026-02-28"));
    verify(repository).ageSexAnimals(context.tenantId(),context.farmId(),LocalDate.of(2026,2,28),true,
        HerdAnimalSex.FEMALE,LocalDate.of(2025,11,28),LocalDate.of(2026,2,28),false,20,0);
    mvc.perform(get("/api/v1/herd/reports/current-age-sex-animals?unknownBirthDate=true&ageBand=MONTHS_0_2"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/reports/historical-age-sex-animals?asOf=2026-09-14"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reconstructsHistoricalBalanceFromEventsWithExplicitProfileSemantics() throws Exception {
    LocalDate asOf = LocalDate.of(2026, 9, 1);
    when(repository.historicalAgeSexCounts(context.tenantId(), context.farmId(), asOf))
        .thenReturn(List.of(new AgeSexCount(HerdAnimalSex.FEMALE, LocalDate.of(2024, 1, 1), 2)));
    mvc.perform(get("/api/v1/herd/reports/historical-age-sex-balance?asOf=2026-09-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positionSemantics")
            .value("RECORDED_FARM_EVENTS_WITH_CURRENTLY_CORRECTED_PROFILE"))
        .andExpect(jsonPath("$.totalActiveAnimals").value(2));
    mvc.perform(get("/api/v1/herd/reports/historical-age-sex-balance?asOf=2026-09-14"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/reports/historical-age-sex-balance?unexpected=true"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reportsRecordedProcedureCoverageWithoutCallingItCompliance() throws Exception {
    when(repository.currentProcedureAgeSexCounts(context.tenantId(), context.farmId(),
        HealthProcedureCode.BRUCELLOSIS)).thenReturn(List.of(
            new ProcedureAgeSexCount(HerdAnimalSex.FEMALE, LocalDate.of(2026, 4, 1), true, 1),
            new ProcedureAgeSexCount(HerdAnimalSex.FEMALE, LocalDate.of(2026, 4, 1), false, 2)));
    mvc.perform(get("/api/v1/herd/reports/current-procedure-coverage"
            + "?procedureCode=BRUCELLOSIS&referenceDate=2026-09-13"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positionSemantics")
            .value("CURRENT_STATE_AGED_AT_REFERENCE_EFFECTIVE_RECORDED_TREATMENTS"))
        .andExpect(jsonPath("$.totalActiveAnimals").value(3))
        .andExpect(jsonPath("$.withRecordedTreatment").value(1))
        .andExpect(jsonPath("$.withoutRecordedTreatment").value(2));
    mvc.perform(get("/api/v1/herd/reports/current-procedure-coverage?procedureCode=BRUCELLOSIS"
            + "&referenceDate=2026-09-14"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/reports/current-procedure-coverage?procedureCode=BRUCELLOSIS"
            + "&tenantId=" + UUID.randomUUID()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exposesPeriodEventReconciliationWithExplicitSemantics() throws Exception {
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 13);
    when(repository.eventLedger(context.tenantId(), context.farmId(), from, to))
        .thenReturn(new EventLedger(10, 1, 2, 1, 3, 1, 1, 9));
    mvc.perform(get("/api/v1/herd/reports/period-reconciliation?from=2026-09-01&to=2026-09-13"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positionSemantics").value("RECORDED_FARM_EVENT_LEDGER"))
        .andExpect(jsonPath("$.balance.openingAnimals").value(10))
        .andExpect(jsonPath("$.balance.closingAnimals").value(9));
    mvc.perform(get("/api/v1/herd/reports/period-reconciliation?from=2026-09-01"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/reports/period-reconciliation?from=2026-09-01&to=2026-09-14"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/herd/reports/period-reconciliation?from=2026-09-01&to=2026-09-13&tenantId=" + UUID.randomUUID()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void filtersEffectiveHealthFactsByStructuredProcedure() throws Exception {
    when(repository.health(eq(context.tenantId()), eq(context.farmId()), any(), any(),
        eq(HealthTreatmentType.VACCINATION), eq(HealthProcedureCode.FOOT_AND_MOUTH_DISEASE),
        isNull(), eq(20), eq(0L)))
        .thenReturn(new ReportPage<>(new HealthSummary(0, 0, Map.of()), List.of(), 0));
    mvc.perform(get("/api/v1/herd/reports/health?treatmentType=VACCINATION&procedureCode=FOOT_AND_MOUTH_DISEASE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.treatmentsCount").value(0));
    mvc.perform(get("/api/v1/herd/reports/health?procedureCode=UNKNOWN"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsMalformedUnknownRepeatedAndOutOfRangeFilters() throws Exception {
    List<MockHttpServletRequestBuilder> invalid =
        List.of(
            get("/api/v1/herd/reports/herd-position?page=-1"),
            get("/api/v1/herd/reports/herd-position?size=0"),
            get("/api/v1/herd/reports/herd-position?size=101"),
            get("/api/v1/herd/reports/herd-position?sex=female"),
            get("/api/v1/herd/reports/herd-position?category=CALF"),
            get("/api/v1/herd/reports/herd-position?paddockId=bad"),
            get("/api/v1/herd/reports/lifecycle?event=born"),
            get("/api/v1/herd/reports/lifecycle?from=bad"),
            get("/api/v1/herd/reports/lifecycle?from=2026-02-01&to=2026-01-01"),
            get("/api/v1/herd/reports/lifecycle?from=2010-01-01&to=2026-01-01"),
            get("/api/v1/herd/reports/movements?sourcePaddockId=bad"),
            get("/api/v1/herd/reports/transfers?direction=in"),
            get("/api/v1/herd/reports/weights?animalId=bad"),
            get("/api/v1/herd/reports/weights?category=CALF"),
            get("/api/v1/herd/reports/health?treatmentType=vaccination"),
            get("/api/v1/herd/reports/reproduction?pregnancyStatus=possible"),
            get("/api/v1/herd/reports/planner?status=open"),
            get("/api/v1/herd/reports/planner?unknown=true"),
            get("/api/v1/herd/reports/planner?page=0&page=1"));

    for (MockHttpServletRequestBuilder request : invalid) {
      mvc.perform(request)
          .andExpect(status().isBadRequest())
          .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
          .andExpect(jsonPath("$.code").value("HERD_QUERY_INVALID"));
    }
    verifyNoInteractions(repository);
  }

  @Test
  void exposesTheConsistentTypedPaginationEnvelope() throws Exception {
    UUID animalId = UUID.randomUUID();
    when(repository.herdPosition(any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(
            new ReportPage<>(
                new HerdPositionSummary(
                    1,
                    Map.of(HerdReportCategory.UNCLASSIFIED, 1L),
                    Map.of(HerdAnimalSex.FEMALE, 1L),
                    List.of(),
                    1),
                List.of(
                    new HerdPositionItem(
                        new AnimalReference(animalId, "A-1", "Aurora"),
                        HerdReportCategory.UNCLASSIFIED,
                        HerdAnimalSex.FEMALE,
                        LocalDate.of(2024, 1, 1),
                        null)),
                1));

    mvc.perform(get("/api/v1/herd/reports/herd-position?category=UNCLASSIFIED&page=0&size=10"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
        .andExpect(jsonPath("$.*", hasSize(6)))
        .andExpect(jsonPath("$.summary.totalActiveAnimals").value(1))
        .andExpect(jsonPath("$.summary.totalsByCategory.UNCLASSIFIED").value(1))
        .andExpect(jsonPath("$.items[0].animal.id").value(animalId.toString()))
        .andExpect(jsonPath("$.items[0].animal.farmId").doesNotExist())
        .andExpect(jsonPath("$.items[0].animal.paddockId").doesNotExist())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void usesTheInjectedClockForPlannerOverdueSemantics() throws Exception {
    when(repository.planner(
            any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(new ReportPage<>(new PlannerSummary(0, 0, 0, 0), List.of(), 0));

    mvc.perform(get("/api/v1/herd/reports/planner")).andExpect(status().isOk());

    verify(repository)
        .planner(
            eq(context.tenantId()),
            eq(context.farmId()),
            eq(LocalDate.of(2025, 9, 14)),
            eq(LocalDate.of(2026, 9, 13)),
            eq(LocalDate.of(2026, 9, 13)),
            isNull(),
            isNull(),
            isNull(),
            eq(20),
            eq(0L));
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
