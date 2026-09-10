package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import org.junit.jupiter.api.*;
import java.time.*; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;

class CorrectCurrentFarmAnimalTest {
    private final TenantContext owner=new TenantContext(new TenantId(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"OWNER","ALL_FARMS");
    private final HerdAnimalProfileRepository repository=mock(HerdAnimalProfileRepository.class);
    private final TenantTransactionExecutor transactions=mock(TenantTransactionExecutor.class);
    private final CorrectCurrentFarmAnimal useCase=new CorrectCurrentFarmAnimal(transactions,repository,Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"),ZoneOffset.UTC));
    private final UUID id=UUID.randomUUID();
    @BeforeEach void transaction(){doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute()).when(transactions).execute(any(), any(TenantTransactionalOperation.class));}
    private HerdAnimalSummary animal(long version){return new HerdAnimalSummary(id,"A-001","Brisa",HerdAnimalSex.FEMALE,LocalDate.of(2024,1,1),HerdAnimalStatus.ACTIVE,version);}
    private CorrectCurrentFarmAnimalCommand patch(long version,HerdAnimalPatch<String> name){return new CorrectCurrentFarmAnimalCommand(version,HerdAnimalPatch.absent(),name,HerdAnimalPatch.absent(),HerdAnimalPatch.absent());}
    @Test void noOpDoesNotUpdateAfterVersionCheck(){when(repository.findByIdForCorrection(owner.tenantId(),owner.farmId(),id)).thenReturn(Optional.of(animal(0)));assertThat(useCase.execute(owner,id,patch(0,HerdAnimalPatch.present(" Brisa ")))).isEqualTo(animal(0));verify(repository,never()).update(any(),any(),any(),anyLong(),any(),any(),any(),any());}
    @Test void staleVersionConflictsBeforeUpdate(){when(repository.findByIdForCorrection(owner.tenantId(),owner.farmId(),id)).thenReturn(Optional.of(animal(1)));assertThatThrownBy(()->useCase.execute(owner,id,patch(0,HerdAnimalPatch.present("Outra")))).isInstanceOf(HerdAnimalVersionConflictException.class);verify(repository,never()).update(any(),any(),any(),anyLong(),any(),any(),any(),any());}
    @Test void viewerIsDeniedBeforeTransactionOrRepository(){var viewer=new TenantContext(owner.tenantId(),owner.userId(),owner.farmId(),owner.membershipId(),"VIEWER","ALL_FARMS");assertThatThrownBy(()->useCase.execute(viewer,id,patch(0,HerdAnimalPatch.present("Outra")))).isInstanceOf(HerdAnimalCorrectionForbiddenException.class);verifyNoInteractions(transactions,repository);}
    @Test void explicitNullClearsName(){when(repository.findByIdForCorrection(owner.tenantId(),owner.farmId(),id)).thenReturn(Optional.of(animal(0)));when(repository.update(any(),any(),any(),eq(0L),any(),isNull(),any(),any())).thenReturn(Optional.of(new HerdAnimalSummary(id,"A-001",null,HerdAnimalSex.FEMALE,LocalDate.of(2024,1,1),HerdAnimalStatus.ACTIVE,1)));assertThat(useCase.execute(owner,id,patch(0,HerdAnimalPatch.present(null))).name()).isNull();}
}
