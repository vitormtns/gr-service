package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteConflictException;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteRepository;
import com.gerenciadorrural.modules.herd.domain.NewHerdAnimal;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

@Service
public class CreateCurrentFarmAnimal {

    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");

    private final TenantTransactionExecutor transactions;
    private final HerdAnimalWriteRepository repository;
    private final Clock clock;

    public CreateCurrentFarmAnimal(
            TenantTransactionExecutor transactions,
            HerdAnimalWriteRepository repository,
            Clock clock
    ) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateCurrentFarmAnimalResult execute(TenantContext context, CreateCurrentFarmAnimalCommand command) {
        Objects.requireNonNull(context, "O contexto de tenant é obrigatório");
        authorize(context);
        NewHerdAnimal animal = normalized(command, context);
        return transactions.execute(context, () -> createOrReplay(animal));
    }

    private CreateCurrentFarmAnimalResult createOrReplay(NewHerdAnimal proposed) {
        return repository.findById(proposed.tenantId(), proposed.farmId(), proposed.id())
                .map(existing -> replay(existing, proposed))
                .orElseGet(() -> insert(proposed));
    }

    private CreateCurrentFarmAnimalResult insert(NewHerdAnimal proposed) {
        try {
            return new CreateCurrentFarmAnimalResult(
                    CreateCurrentFarmAnimalResult.Outcome.CREATED,
                    repository.insert(proposed)
            );
        } catch (HerdAnimalWriteConflictException conflict) {
            if (conflict.type() == HerdAnimalWriteConflictException.Type.IDENTIFICATION_CONFLICT) {
                throw new HerdAnimalIdentificationConflictException();
            }
            if (conflict.type() == HerdAnimalWriteConflictException.Type.ID_CONFLICT) {
                return repository.findById(proposed.tenantId(), proposed.farmId(), proposed.id())
                        .map(existing -> replay(existing, proposed))
                        .orElseThrow(HerdAnimalIdempotencyConflictException::new);
            }
            throw conflict;
        }
    }

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
        if (identification.isEmpty() || identification.length() > 100
                || (name != null && (name.isEmpty() || name.length() > 255))
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
