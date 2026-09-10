package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalInsertResult;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteConflictException;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteRepository;
import com.gerenciadorrural.modules.herd.domain.NewHerdAnimal;
import com.gerenciadorrural.modules.herd.domain.AnimalEventRepository;
import com.gerenciadorrural.modules.herd.domain.AnimalEventType;
import com.gerenciadorrural.modules.herd.domain.AnimalEventDetails;
import com.gerenciadorrural.modules.herd.domain.CreatedEventDetails;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import org.springframework.stereotype.Service; import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateCurrentFarmAnimal {

    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");

    private final TenantTransactionExecutor transactions;
    private final HerdAnimalWriteRepository repository;
    private final Clock clock;
    private final AnimalEventRepository events;

    @Autowired
    public CreateCurrentFarmAnimal(
            TenantTransactionExecutor transactions,
            HerdAnimalWriteRepository repository,
            Clock clock, AnimalEventRepository events
    ) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
        this.events = Objects.requireNonNull(events);
    }

    public CreateCurrentFarmAnimalResult execute(TenantContext context, CreateCurrentFarmAnimalCommand command) {
        Objects.requireNonNull(context, "O contexto de tenant é obrigatório");
        authorize(context);
        NewHerdAnimal animal = normalized(command, context);
        return transactions.execute(context, () -> createOrReplay(animal, context.userId()));
    }

    private CreateCurrentFarmAnimalResult createOrReplay(NewHerdAnimal proposed, UUID actorUserId) {
        return repository.findById(proposed.tenantId(), proposed.farmId(), proposed.id())
                .map(existing -> replay(existing, proposed))
                .orElseGet(() -> insert(proposed, actorUserId));
    }

    private CreateCurrentFarmAnimalResult insert(NewHerdAnimal proposed, UUID actorUserId) {
        try {
            HerdAnimalInsertResult result = repository.insert(proposed);
            if (result.outcome() == HerdAnimalInsertResult.Outcome.INSERTED) {
                HerdAnimalSummary animal=result.animal().orElseThrow();
                events.record(proposed.tenantId(), proposed.farmId(), animal.id(), AnimalEventType.CREATED, null,
                        actorUserId, null, 0, new CreatedEventDetails(animal.identification(), animal.name(), animal.sex().name(), animal.birthDate()));
                return new CreateCurrentFarmAnimalResult(
                        CreateCurrentFarmAnimalResult.Outcome.CREATED,
                        animal
                );
            }
            return repository.findById(proposed.tenantId(), proposed.farmId(), proposed.id())
                    .map(existing -> replay(existing, proposed))
                    .orElseThrow(HerdAnimalIdempotencyConflictException::new);
        } catch (HerdAnimalWriteConflictException conflict) {
            if (conflict.type() == HerdAnimalWriteConflictException.Type.IDENTIFICATION_CONFLICT) {
                throw new HerdAnimalIdentificationConflictException();
            }
            throw conflict;
        }
    }
    public CreateCurrentFarmAnimal(TenantTransactionExecutor transactions,HerdAnimalWriteRepository repository,Clock clock){this(transactions,repository,clock,new NoopEvents());}

    private static final class NoopEvents implements AnimalEventRepository { public void record(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID a,AnimalEventType y,UUID o,UUID u,LocalDate d,long v,AnimalEventDetails p){} public java.util.Optional<com.gerenciadorrural.modules.herd.domain.AnimalEvent> findByOperation(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID o){return java.util.Optional.empty();} public java.util.List<com.gerenciadorrural.modules.herd.domain.AnimalEvent> history(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID a,AnimalEventType y,int s,long z){return java.util.List.of();} public long count(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID a,AnimalEventType y){return 0;} public void lockOperation(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID o){} }

    private CreateCurrentFarmAnimalResult replay(HerdAnimalSummary existing, NewHerdAnimal proposed) {
        if (!sameCreationPayload(existing, proposed)) {
            throw new HerdAnimalIdempotencyConflictException();
        }
        return new CreateCurrentFarmAnimalResult(CreateCurrentFarmAnimalResult.Outcome.REPLAYED, existing);
    }

    private static boolean sameCreationPayload(HerdAnimalSummary existing, NewHerdAnimal proposed) {
        return existing.id().equals(proposed.id())
                && existing.identification().equals(proposed.identification())
                && Objects.equals(existing.name(), proposed.name())
                && existing.sex() == proposed.sex()
                && Objects.equals(existing.birthDate(), proposed.birthDate());
    }

    private NewHerdAnimal normalized(CreateCurrentFarmAnimalCommand command, TenantContext context) {
        if (command == null || command.id() == null || command.identification() == null || command.sex() == null) {
            throw new HerdAnimalCommandInvalidException();
        }
        String identification = PosixEdgeWhitespace.trim(command.identification());
        String name = command.name() == null ? null : PosixEdgeWhitespace.trim(command.name());
        if (identification.isEmpty() || identification.indexOf('\u0000') >= 0 || identification.codePointCount(0, identification.length()) > 100
                || (name != null && (name.isEmpty() || name.indexOf('\u0000') >= 0 || name.codePointCount(0, name.length()) > 255))
                || (command.birthDate() != null && command.birthDate().isAfter(LocalDate.now(clock)))) {
            throw new HerdAnimalCommandInvalidException();
        }
        return new NewHerdAnimal(command.id(), context.tenantId(), context.farmId(), identification, name,
                command.sex(), command.birthDate());
    }

    private static void authorize(TenantContext context) {
        if (!ALLOWED_ROLES.contains(context.role())) {
            throw new HerdAnimalCreationForbiddenException();
        }
    }
}
