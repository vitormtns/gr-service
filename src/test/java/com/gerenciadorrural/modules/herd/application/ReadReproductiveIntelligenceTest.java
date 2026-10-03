package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReadReproductiveIntelligenceTest {
  @Test void previewUsesCalendarPolicyAndValidatedRole() {
    var tx=mock(TenantTransactionExecutor.class);
    var service=new ReadReproductiveIntelligence(tx,mock(HerdReproductionRepository.class),
        mock(HerdAnimalProfileRepository.class),Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"),ZoneOffset.UTC),30);
    var c=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"VIEWER","ALL_FARMS");
    var preview=service.preview(c,LocalDate.of(2026,1,1));
    assertThat(preview.expectedCalvingOn()).isEqualTo(LocalDate.of(2026,10,11));
    assertThat(preview.gestationDays()).isEqualTo(283);
    assertThatThrownBy(()->service.preview(c,LocalDate.of(2026,10,3))).isInstanceOf(HerdAnimalCommandInvalidException.class);
    var denied=new TenantContext(c.tenantId(),c.userId(),c.farmId(),c.membershipId(),"INVALID","ALL_FARMS");
    assertThatThrownBy(()->service.preview(denied,LocalDate.of(2026,1,1))).isInstanceOf(HerdMovementForbiddenException.class);
    verifyNoInteractions(tx);
  }
  @Test void missingAnimalDoesNotDiscloseAnyReproductiveFacts() {
    var tx=mock(TenantTransactionExecutor.class);var animals=mock(HerdAnimalProfileRepository.class);
    var repo=mock(HerdReproductionRepository.class);
    doAnswer(i->((TenantTransactionalOperation<?>)i.getArgument(1)).execute()).when(tx)
        .execute(any(),org.mockito.ArgumentMatchers.<TenantTransactionalOperation<Object>>any());
    var service=new ReadReproductiveIntelligence(tx,repo,animals,Clock.systemUTC(),45);
    var c=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"VIEWER","ALL_FARMS");
    assertThatThrownBy(()->service.animal(c,UUID.randomUUID())).isInstanceOf(HerdAnimalNotFoundException.class);
    verifyNoInteractions(repo);
  }
}
