package com.gerenciadorrural.modules.herd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.*;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HerdGroupServiceTest {
  private final TenantContext owner = new TenantContext(new TenantId(UUID.randomUUID()),
      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OWNER", "ALL_FARMS");
  private final TenantContext viewer = new TenantContext(owner.tenantId(), owner.userId(),
      owner.farmId(), owner.membershipId(), "VIEWER", "ALL_FARMS");
  private final UUID groupId = UUID.randomUUID(), animalId = UUID.randomUUID();
  private final HerdGroupRepository repository = mock(HerdGroupRepository.class);
  private final TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
  private final HerdGroupService service = new HerdGroupService(transactions, repository,
      Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC));

  @BeforeEach
  void setup() {
    doAnswer(invocation -> ((TenantTransactionalOperation<?>) invocation.getArgument(1)).execute())
        .when(transactions).execute(any(), any(TenantTransactionalOperation.class));
  }

  @Test
  void createsIdempotentlyAndRejectsConflictingReuse() {
    HerdGroup manual = manual(0);
    when(repository.insert(owner.tenantId(), owner.farmId(), manual)).thenReturn(manual);
    assertThat(service.create(owner, groupId, " Lote A ", HerdGroup.Kind.MANUAL, null).created())
        .isTrue();
    when(repository.find(owner.tenantId(), owner.farmId(), groupId, false))
        .thenReturn(Optional.of(manual));
    assertThat(service.create(owner, groupId, "Lote A", HerdGroup.Kind.MANUAL, null).created())
        .isFalse();
    assertThatThrownBy(() -> service.create(owner, groupId, "Outro nome",
        HerdGroup.Kind.MANUAL, null)).isInstanceOf(HerdGroupConflictException.class);
    assertThatThrownBy(() -> service.create(viewer, UUID.randomUUID(), "Lote B",
        HerdGroup.Kind.MANUAL, null)).isInstanceOf(HerdGroupForbiddenException.class);
  }

  @Test
  void membershipRequiresManualGroupMatchingVersionAndSameFarmAnimal() {
    when(repository.find(owner.tenantId(), owner.farmId(), groupId, true))
        .thenReturn(Optional.of(manual(0)));
    when(repository.animalExists(owner.tenantId(), owner.farmId(), animalId)).thenReturn(true);
    when(repository.bumpVersion(owner.tenantId(), owner.farmId(), groupId, 0))
        .thenReturn(Optional.of(manual(1)));
    assertThat(service.membership(owner, groupId, animalId, 0L, true).version()).isEqualTo(1);
    verify(repository).addMember(owner.tenantId(), owner.farmId(), groupId, animalId);
    assertThatThrownBy(() -> service.membership(owner, groupId, animalId, 1L, true))
        .isInstanceOf(HerdAnimalVersionConflictException.class);
    when(repository.find(owner.tenantId(), owner.farmId(), groupId, true))
        .thenReturn(Optional.of(new HerdGroup(groupId, "Dinâmico", HerdGroup.Kind.SMART,
            HerdGroup.Status.ACTIVE, HerdGroup.Rules.empty(), 0)));
    assertThatThrownBy(() -> service.membership(owner, groupId, animalId, 0L, true))
        .isInstanceOf(HerdGroupConflictException.class);
  }

  @Test
  void rejectsInvalidAgeRulesAndFutureReference() {
    assertThatThrownBy(() -> service.create(owner, groupId, "Inválido", HerdGroup.Kind.SMART,
        new HerdGroup.Rules(null, null, 20, 10, false, false)))
        .isInstanceOf(HerdAnimalCommandInvalidException.class);
    assertThatThrownBy(() -> service.create(owner, groupId, "Inválido", HerdGroup.Kind.MANUAL,
        new HerdGroup.Rules(HerdAnimalSex.FEMALE, null, null, null, false, false)))
        .isInstanceOf(HerdAnimalCommandInvalidException.class);
    assertThatThrownBy(() -> service.animals(owner, groupId, LocalDate.of(2026, 9, 28), 0, 20))
        .isInstanceOf(HerdAnimalCommandInvalidException.class);
  }

  private HerdGroup manual(long version) {
    return new HerdGroup(groupId, "Lote A", HerdGroup.Kind.MANUAL, HerdGroup.Status.ACTIVE,
        HerdGroup.Rules.empty(), version);
  }
}
