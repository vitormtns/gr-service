package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReadAnimalLineageTest {
  @Test void derivesRelativeAgesFromExplicitClockAndPreservesUnknownBirthDate() {
    var tx=mock(TenantTransactionExecutor.class);var animals=mock(HerdAnimalProfileRepository.class);
    var repo=mock(AnimalLineageRepository.class);UUID self=UUID.randomUUID(),child=UUID.randomUUID();
    var c=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"VIEWER","ALL_FARMS");
    doAnswer(i->((TenantTransactionalOperation<?>)i.getArgument(1)).execute()).when(tx)
        .execute(any(),org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
    when(animals.findById(c.tenantId(),c.farmId(),self)).thenReturn(Optional.of(mock(HerdAnimalSummary.class)));
    when(repo.lineage(c.tenantId(),c.farmId(),self,5,101)).thenReturn(List.of(
        new AnimalLineageRepository.Node(child,"C01",null,LocalDate.of(2026,7,4),HerdAnimalSex.FEMALE,"DESCENDANT",1,self,false),
        new AnimalLineageRepository.Node(UUID.randomUUID(),"M01",null,null,HerdAnimalSex.FEMALE,"ANCESTOR",1,self,false)));
    var service=new ReadAnimalLineage(tx,animals,repo,Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"),ZoneOffset.UTC));
    var result=service.read(c,self,5,100);
    assertThat(result.referenceDate()).isEqualTo(LocalDate.of(2026,10,3));
    assertThat(result.items().getFirst().age().completedMonths()).isEqualTo(2);
    assertThat(result.items().getFirst().age().currentBand()).isEqualTo(AgeBand.MONTHS_0_2);
    assertThat(result.items().get(1).age()).isNull();
  }
}
