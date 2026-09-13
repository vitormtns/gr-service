package com.gerenciadorrural.modules.herd.domain;

import com.gerenciadorrural.shared.tenancy.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HerdAgendaRepository {

  List<Row> page(
      TenantId tenant,
      UUID farm,
      LocalDate reference,
      int weighingDays,
      int upcomingDays,
      HerdAgendaSource source,
      HerdPlannerType type,
      UUID animal,
      LocalDate from,
      LocalDate to,
      int size,
      long offset);

  long count(
      TenantId tenant,
      UUID farm,
      LocalDate reference,
      int weighingDays,
      int upcomingDays,
      HerdAgendaSource source,
      HerdPlannerType type,
      UUID animal,
      LocalDate from,
      LocalDate to);

  record Row(
      HerdAgendaSource source,
      String kind,
      LocalDate operationalDate,
      String stableId,
      UUID animalId,
      String summary,
      String identification,
      String name,
      UUID plannerItemId,
      PendingWorkType pendingWorkType,
      UUID pregnancyId,
      HerdPlannerStatus status) {}
}
