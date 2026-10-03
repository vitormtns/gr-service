package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadHerdPendingReasonTest {
  @Test void exposesActualConfiguredWeighingWindowAndCalvingAttentionInJson() throws Exception {
    LocalDate today=LocalDate.of(2026,10,2);
    var weight=new HerdManagementRepository.Pending(PendingWorkType.WEIGHING_DUE,UUID.randomUUID(),
        "M01",null,UUID.randomUUID(),today.minusDays(73),null,null,null,today.minusDays(73));
    var item=ReadHerdPendingWork.Item.from(weight,today,73);
    assertThat(item.reason().windowDays()).isEqualTo(73);
    assertThat(item.reason().cutoffOn()).isEqualTo(today.minusDays(73));
    assertThat(item.reason().sourceDate()).isEqualTo(today.minusDays(73));
    var pending=new HerdManagementRepository.Pending(PendingWorkType.CALVING_OVERDUE,UUID.randomUUID(),
        "F01",null,UUID.randomUUID(),today.minusDays(14),UUID.randomUUID(),null,null,null);
    var json=new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(ReadHerdPendingWork.Item.from(pending,today,73));
    assertThat(json.path("calvingAttention").path("level").asText()).isEqualTo("OVERDUE_EXTENDED");
    assertThat(json.path("calvingAttention").path("daysOverdue").asInt()).isEqualTo(14);
  }
}
