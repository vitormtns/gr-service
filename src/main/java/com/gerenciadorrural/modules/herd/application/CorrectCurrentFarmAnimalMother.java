package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CorrectCurrentFarmAnimalMother {
    public record Result(UUID animalId, UUID motherId, long version, boolean replayed) {}

    private static final Set<String> WRITE = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");
    private final TenantTransactionExecutor transactions;
    private final HerdAnimalProfileRepository animals;
    private final MaternalRelationRepository relations;
    private final AnimalEventRepository events;

    public CorrectCurrentFarmAnimalMother(TenantTransactionExecutor transactions,
            HerdAnimalProfileRepository animals, MaternalRelationRepository relations,
            AnimalEventRepository events) {
        this.transactions = transactions;
        this.animals = animals;
        this.relations = relations;
        this.events = events;
    }

    public Result execute(TenantContext context, UUID animalId, UUID operationId,
            Long expectedVersion, UUID motherId) {
        Objects.requireNonNull(context);
        if (!WRITE.contains(context.role())) throw new HerdAnimalCorrectionForbiddenException();
        if (animalId == null || operationId == null || expectedVersion == null || expectedVersion < 0)
            throw new HerdAnimalCommandInvalidException();
        return transactions.execute(context, () -> {
            events.lockOperation(context.tenantId(), context.farmId(), operationId);
            var previous = events.findByOperation(context.tenantId(), context.farmId(), operationId);
            if (previous.isPresent()) {
                AnimalEvent event = previous.get();
                if (event.type() != AnimalEventType.MOTHER_CORRECTED || !event.animalId().equals(animalId)
                        || !(event.details() instanceof MotherCorrectedEventDetails details)
                        || details.expectedVersion() != expectedVersion
                        || !Objects.equals(details.afterMotherId(), motherId))
                    throw new HerdOperationIdempotencyConflictException();
                return new Result(animalId, motherId, event.resultingVersion(), true);
            }
            relations.lockFarm(context.tenantId(), context.farmId());
            HerdAnimalSummary child = animals.findByIdForCorrection(context.tenantId(), context.farmId(), animalId)
                    .orElseThrow(HerdAnimalNotFoundException::new);
            if (child.version() != expectedVersion) throw new HerdAnimalVersionConflictException();
            var current = relations.relation(context.tenantId(), animalId);
            if (current.isPresent() && current.get().pregnancyId() != null)
                throw new HerdMotherCorrectionConflictException();
            UUID before = current.map(MaternalRelationRepository.Relation::motherId).orElse(null);
            if (motherId != null) {
                HerdAnimalSummary mother = animals.findById(context.tenantId(), context.farmId(), motherId)
                        .orElseThrow(HerdAnimalNotFoundException::new);
                if (mother.sex() != HerdAnimalSex.FEMALE || motherId.equals(animalId)
                        || (mother.birthDate() != null && child.birthDate() != null
                        && !mother.birthDate().isBefore(child.birthDate())))
                    throw new HerdAnimalCommandInvalidException();
                UUID ancestor = motherId;
                Set<UUID> visited = new HashSet<>();
                while (ancestor != null) {
                    if (ancestor.equals(animalId) || !visited.add(ancestor))
                        throw new HerdAnimalCommandInvalidException();
                    ancestor = relations.motherId(context.tenantId(), ancestor).orElse(null);
                }
            }
            try {
                if (!Objects.equals(before, motherId)) {
                    if (before == null) {
                        relations.insert(context.tenantId(), motherId, animalId, null);
                    } else if (motherId == null) {
                        if (!relations.remove(context.tenantId(), animalId, before))
                            throw new HerdMotherCorrectionConflictException();
                    } else if (!relations.change(context.tenantId(), animalId, before, motherId)) {
                        throw new HerdMotherCorrectionConflictException();
                    }
                }
                HerdAnimalSummary updated = animals.touch(context.tenantId(), context.farmId(), animalId,
                        expectedVersion).orElseThrow(HerdAnimalVersionConflictException::new);
                events.record(context.tenantId(), context.farmId(), animalId, AnimalEventType.MOTHER_CORRECTED,
                        operationId, context.userId(), null, updated.version(),
                        new MotherCorrectedEventDetails(before, motherId, expectedVersion));
                return new Result(animalId, motherId, updated.version(), false);
            } catch (DataIntegrityViolationException error) {
                throw new HerdMotherCorrectionConflictException();
            }
        });
    }
}
