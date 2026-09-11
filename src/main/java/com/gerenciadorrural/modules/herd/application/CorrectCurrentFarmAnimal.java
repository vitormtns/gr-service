package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.*;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import org.springframework.stereotype.Service; import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CorrectCurrentFarmAnimal {
    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");
    private final TenantTransactionExecutor transactions;
    private final HerdAnimalProfileRepository repository;
    private final Clock clock;
    private final AnimalEventRepository events;

    @Autowired public CorrectCurrentFarmAnimal(TenantTransactionExecutor transactions, HerdAnimalProfileRepository repository, Clock clock, AnimalEventRepository events) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
        this.events = Objects.requireNonNull(events);
    }
    public CorrectCurrentFarmAnimal(TenantTransactionExecutor transactions,HerdAnimalProfileRepository repository,Clock clock){this(transactions,repository,clock,new AnimalEventRepository(){public void record(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID a,AnimalEventType y,UUID o,UUID u,LocalDate d,long v,AnimalEventDetails p){}public java.util.Optional<AnimalEvent> findByOperation(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID o){return java.util.Optional.empty();}public java.util.List<AnimalEvent> history(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID a,AnimalEventType y,int s,long z){return java.util.List.of();}public long count(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID a,AnimalEventType y){return 0;}public void lockOperation(com.gerenciadorrural.shared.tenancy.TenantId t,UUID f,UUID o){}});}

    public HerdAnimalSummary execute(TenantContext context, UUID id, CorrectCurrentFarmAnimalCommand command) {
        Objects.requireNonNull(context, "O contexto de tenant é obrigatório");
        authorize(context);
        ValidPatch patch = validate(command);
        return transactions.execute(context, () -> correct(context, id, patch));
    }

    private HerdAnimalSummary correct(TenantContext context, UUID id, ValidPatch patch) {
        HerdAnimalSummary current = repository.findByIdForCorrection(context.tenantId(), context.farmId(), id)
                .orElseThrow(HerdAnimalNotFoundException::new);
        if (current.version() != patch.expectedVersion) throw new HerdAnimalVersionConflictException();
        HerdAnimalSummary target = apply(current, patch);
        if (sameEditableState(current, target)) return current;
        try {
            HerdAnimalSummary persisted=repository.update(context.tenantId(), context.farmId(), id, patch.expectedVersion,
                    target.identification(), target.name(), target.sex(), target.birthDate())
                    .orElseGet(() -> afterMiss(context, id));
            HerdAnimalSummary updated=new HerdAnimalSummary(persisted.id(),persisted.identification(),persisted.name(),persisted.sex(),persisted.birthDate(),persisted.status(),persisted.version(),current.paddock());
            events.record(context.tenantId(),context.farmId(),id,AnimalEventType.CORRECTED,null,context.userId(),null,updated.version(),changes(current,updated));
            return updated;
        } catch (HerdAnimalWriteConflictException conflict) {
            if (conflict.type() == HerdAnimalWriteConflictException.Type.IDENTIFICATION_CONFLICT) {
                throw new HerdAnimalIdentificationConflictException();
            }
            throw conflict;
        }
    }

    private HerdAnimalSummary afterMiss(TenantContext context, UUID id) {
        HerdAnimalSummary current = repository.findById(context.tenantId(), context.farmId(), id)
                .orElseThrow(HerdAnimalNotFoundException::new);
        throw new HerdAnimalVersionConflictException();
    }

    private ValidPatch validate(CorrectCurrentFarmAnimalCommand command) {
        if (command == null || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.identification() == null || command.name() == null || command.sex() == null || command.birthDate() == null
                || !(command.identification().present() || command.name().present() || command.sex().present() || command.birthDate().present())) {
            throw new HerdAnimalCommandInvalidException();
        }
        String identification = command.identification().value();
        String name = command.name().value();
        if (command.identification().present()) {
            if (identification == null) throw new HerdAnimalCommandInvalidException();
            identification = PosixEdgeWhitespace.trim(identification);
            if (invalid(identification, 100)) throw new HerdAnimalCommandInvalidException();
        }
        if (command.name().present() && name != null) {
            name = PosixEdgeWhitespace.trim(name);
            if (invalid(name, 255)) throw new HerdAnimalCommandInvalidException();
        }
        if (command.sex().present() && command.sex().value() == null) throw new HerdAnimalCommandInvalidException();
        if (command.birthDate().present() && command.birthDate().value() != null && command.birthDate().value().isAfter(LocalDate.now(clock))) throw new HerdAnimalCommandInvalidException();
        return new ValidPatch(command.expectedVersion(), new HerdAnimalPatch<>(command.identification().present(), identification),
                new HerdAnimalPatch<>(command.name().present(), name), command.sex(), command.birthDate());
    }
    private static boolean invalid(String value, int max) { return value.isEmpty() || value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > max; }
    private static HerdAnimalSummary apply(HerdAnimalSummary current, ValidPatch patch) { return new HerdAnimalSummary(current.id(), patch.identification.present()?patch.identification.value():current.identification(), patch.name.present()?patch.name.value():current.name(), patch.sex.present()?patch.sex.value():current.sex(), patch.birthDate.present()?patch.birthDate.value():current.birthDate(), current.status(), current.version(),current.paddock()); }
    private static boolean sameEditableState(HerdAnimalSummary a, HerdAnimalSummary b) { return a.identification().equals(b.identification()) && Objects.equals(a.name(), b.name()) && a.sex()==b.sex() && Objects.equals(a.birthDate(), b.birthDate()); }
    private static void authorize(TenantContext context) { if (!ALLOWED_ROLES.contains(context.role())) throw new HerdAnimalCorrectionForbiddenException(); }
    private static CorrectedEventDetails changes(HerdAnimalSummary before,HerdAnimalSummary after){java.util.Map<String,FieldChange> changes=new java.util.LinkedHashMap<>(); add(changes,"identification",before.identification(),after.identification());add(changes,"name",before.name(),after.name());add(changes,"sex",before.sex().name(),after.sex().name());add(changes,"birthDate",before.birthDate()==null?null:before.birthDate().toString(),after.birthDate()==null?null:after.birthDate().toString());return new CorrectedEventDetails(changes);}
    private static void add(java.util.Map<String,FieldChange> changes,String name,String before,String after){if(!Objects.equals(before,after))changes.put(name,new FieldChange(before,after));}
    private record ValidPatch(long expectedVersion, HerdAnimalPatch<String> identification, HerdAnimalPatch<String> name, HerdAnimalPatch<HerdAnimalSex> sex, HerdAnimalPatch<LocalDate> birthDate) { }
}
