package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReadHerdAgendaDailySummaryTest {
  @Test void overdueAggregationUsesOriginalDatesForSeverityAndTodayForDisplay() {
    var tx=mock(TenantTransactionExecutor.class);var repo=mock(HerdAgendaRepository.class);
    var herd=mock(HerdManagementRepository.class);
    doAnswer(i->((TenantTransactionalOperation<?>)i.getArgument(1)).execute()).when(tx)
        .execute(any(),org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
    LocalDate today=LocalDate.of(2026,10,2);
    var c=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"VIEWER","ALL_FARMS");
    when(repo.dailyCounts(c.tenantId(),c.farmId(),today,73,14,null,HerdPlannerType.CALVING,null,null,today.plusDays(2)))
        .thenReturn(List.of(new HerdAgendaRepository.DailyCount(today.minusDays(1),105),
            new HerdAgendaRepository.DailyCount(today,2),new HerdAgendaRepository.DailyCount(today.plusDays(1),3)));
    var service=new ReadHerdAgenda(tx,repo,herd,Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC),73,14);
    var result=service.dailySummary(c,null,HerdPlannerType.CALVING,null,today,today.plusDays(2),true);
    assertThat(result.totalElements()).isEqualTo(110);
    assertThat(result.days()).containsExactly(new ReadHerdAgenda.DaySummary(today,107,"DANGER"),
        new ReadHerdAgenda.DaySummary(today.plusDays(1),3,"INFO"));
    assertThatThrownBy(()->service.dailySummary(c,null,null,null,today.minusDays(1),today,true))
        .isInstanceOf(HerdPlannerExceptions.QueryInvalid.class);
    assertThatThrownBy(()->service.dailySummary(c,null,null,null,today,today.plusDays(366),false))
        .isInstanceOf(HerdPlannerExceptions.QueryInvalid.class);
  }
}
