package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.AnimalEventRepository;
import com.gerenciadorrural.modules.herd.domain.AnimalEventType;
import com.gerenciadorrural.modules.herd.domain.AnimalNoteEventDetails;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalProfileRepository;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecordCurrentFarmAnimalNote {
    public record Result(UUID animalId, UUID operationId, LocalDate occurredOn,
            String notes, long version, boolean replayed) {}

    private static final Set<String> WRITE = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");
    private final TenantTransactionExecutor transactions;
    private final HerdAnimalProfileRepository animals;
    private final AnimalEventRepository events;
    private final Clock clock;

    public RecordCurrentFarmAnimalNote(TenantTransactionExecutor transactions,
            HerdAnimalProfileRepository animals, AnimalEventRepository events, Clock clock) {
        this.transactions = transactions;
        this.animals = animals;
        this.events = events;
        this.clock = clock;
    }

    public Result execute(TenantContext context, UUID animalId, UUID operationId,
            Long expectedVersion, LocalDate occurredOn, String notes) {
        Objects.requireNonNull(context);
        if (!WRITE.contains(context.role())) throw new HerdAnimalCorrectionForbiddenException();
        if (animalId == null || operationId == null || expectedVersion == null || expectedVersion < 0
                || occurredOn == null || occurredOn.isAfter(LocalDate.now(clock)) || notes == null)
            throw new HerdAnimalCommandInvalidException();
        String normalized = PosixEdgeWhitespace.trim(notes);
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0
                || normalized.codePointCount(0, normalized.length()) > 2000)
            throw new HerdAnimalCommandInvalidException();
        return transactions.execute(context, () -> {
            events.lockOperation(context.tenantId(), context.farmId(), operationId);
            var prior = events.findByOperation(context.tenantId(), context.farmId(), operationId);
            if (prior.isPresent()) {
                var event = prior.get();
                if (event.type() != AnimalEventType.NOTE_RECORDED || !event.animalId().equals(animalId)
                        || !(event.details() instanceof AnimalNoteEventDetails details)
                        || details.expectedVersion() != expectedVersion
                        || !details.occurredOn().equals(occurredOn)
                        || !details.notes().equals(normalized))
                    throw new HerdOperationIdempotencyConflictException();
                return new Result(animalId, operationId, occurredOn, normalized,
                        event.resultingVersion(), true);
            }
            var animal = animals.findByIdForCorrection(context.tenantId(), context.farmId(), animalId)
                    .orElseThrow(HerdAnimalNotFoundException::new);
            if (animal.version() != expectedVersion) throw new HerdAnimalVersionConflictException();
            if (animal.birthDate() != null && occurredOn.isBefore(animal.birthDate()))
                throw new HerdAnimalCommandInvalidException();
            var updated = animals.touch(context.tenantId(), context.farmId(), animalId,
                    expectedVersion).orElseThrow(HerdAnimalVersionConflictException::new);
            events.record(context.tenantId(), context.farmId(), animalId, AnimalEventType.NOTE_RECORDED,
                    operationId, context.userId(), occurredOn, updated.version(),
                    new AnimalNoteEventDetails(occurredOn, normalized, expectedVersion));
            return new Result(animalId, operationId, occurredOn, normalized, updated.version(), false);
        });
    }
}
