package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalImportRepository;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalImportRepository.Receipt;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Atomic import of normalized rows; CSV interpretation belongs to the client. */
@Service
public class ImportCurrentFarmAnimals {
    public record Row(UUID id, String identification, String name, HerdAnimalSex sex,
                      HerdAnimalStatus status, LocalDate birthDate, String motherIdentification) {}

    public record Result(List<UUID> animalIds, boolean replayed) {}

    private static final Set<String> WRITE = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");
    private final TenantTransactionExecutor transactions;
    private final CreateCurrentFarmAnimal create;
    private final HerdAnimalImportRepository imports;
    private final Clock clock;

    public ImportCurrentFarmAnimals(TenantTransactionExecutor transactions, CreateCurrentFarmAnimal create,
                                    HerdAnimalImportRepository imports, Clock clock) {
        this.transactions = transactions;
        this.create = create;
        this.imports = imports;
        this.clock = clock;
    }

    public Result execute(TenantContext context, UUID operationId, List<Row> rows) {
        Objects.requireNonNull(context);
        if (!WRITE.contains(context.role())) throw new HerdAnimalCreationForbiddenException();
        if (operationId == null || rows == null || rows.isEmpty() || rows.size() > 100)
            throw new HerdAnimalCommandInvalidException();
        List<Row> normalized = normalize(rows);
        String hash = hash(normalized);
        return transactions.execute(context, () -> {
            imports.lock(context.tenantId(), context.farmId(), operationId);
            var previous = imports.find(context.tenantId(), context.farmId(), operationId);
            if (previous.isPresent()) {
                if (!previous.get().payloadHash().equals(hash)) throw new HerdAnimalIdempotencyConflictException();
                return new Result(previous.get().animalIds(), true);
            }
            imports.lockMaternalGraph(context.tenantId(), context.farmId());
            List<UUID> ids = new ArrayList<>();
            for (Row row : normalized) {
                // The outer tenant transaction makes every insert and event atomic with the receipt.
                var outcome = create.execute(context, new CreateCurrentFarmAnimalCommand(
                        row.id(), row.identification(), row.name(), row.sex(), row.birthDate()));
                if (outcome.outcome() != CreateCurrentFarmAnimalResult.Outcome.CREATED)
                    throw new HerdAnimalIdempotencyConflictException();
                ids.add(row.id());
            }
            for (Row row : normalized) {
                if (row.motherIdentification() == null) continue;
                HerdAnimalSummary mother = imports.findByIdentification(context.tenantId(), context.farmId(),
                        row.motherIdentification()).orElseThrow(HerdAnimalCommandInvalidException::new);
                if (mother.sex() != HerdAnimalSex.FEMALE || mother.id().equals(row.id())
                        || (mother.birthDate() != null && row.birthDate() != null
                        && !mother.birthDate().isBefore(row.birthDate())))
                    throw new HerdAnimalCommandInvalidException();
                Set<UUID> ancestors = new HashSet<>();
                UUID ancestor = mother.id();
                while (ancestor != null) {
                    if (ancestor.equals(row.id()) || !ancestors.add(ancestor))
                        throw new HerdAnimalCommandInvalidException();
                    ancestor = imports.motherId(context.tenantId(), ancestor).orElse(null);
                }
                if (imports.motherId(context.tenantId(), row.id()).isPresent())
                    throw new HerdAnimalIdempotencyConflictException();
                try {
                    imports.linkMother(context.tenantId(), mother.id(), row.id());
                } catch (DataIntegrityViolationException error) {
                    throw new HerdAnimalIdempotencyConflictException();
                }
            }
            imports.save(context.tenantId(), context.farmId(), operationId, new Receipt(hash, List.copyOf(ids)));
            return new Result(List.copyOf(ids), false);
        });
    }

    private List<Row> normalize(List<Row> rows) {
        List<Row> result = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        Set<String> identifications = new HashSet<>();
        for (Row row : rows) {
            if (row == null || row.id() == null || row.identification() == null || row.sex() == null
                    || (row.status() != null && row.status() != HerdAnimalStatus.ACTIVE))
                throw new HerdAnimalCommandInvalidException();
            String identification = PosixEdgeWhitespace.trim(row.identification());
            String name = row.name() == null ? null : PosixEdgeWhitespace.trim(row.name());
            String mother = row.motherIdentification() == null ? null
                    : PosixEdgeWhitespace.trim(row.motherIdentification());
            if (identification.isEmpty() || identification.codePointCount(0, identification.length()) > 100
                    || identification.indexOf('\0') >= 0
                    || (name != null && (name.isEmpty() || name.codePointCount(0, name.length()) > 255
                    || name.indexOf('\0') >= 0))
                    || (mother != null && (mother.isEmpty()
                    || mother.codePointCount(0, mother.length()) > 100 || mother.indexOf('\0') >= 0))
                    || (row.birthDate() != null && row.birthDate().isAfter(LocalDate.now(clock)))
                    || !ids.add(row.id()) || !identifications.add(identification.toLowerCase(Locale.ROOT)))
                throw new HerdAnimalCommandInvalidException();
            result.add(new Row(row.id(), identification, name, row.sex(), HerdAnimalStatus.ACTIVE,
                    row.birthDate(), mother));
        }
        return List.copyOf(result);
    }

    private static String hash(List<Row> rows) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(rows.size());
            for (Row row : rows) {
                output.writeUTF(row.id().toString());
                output.writeUTF(row.identification());
                writeNullable(output, row.name());
                output.writeUTF(row.sex().name());
                output.writeUTF(row.status().name());
                writeNullable(output, row.birthDate() == null ? null : row.birthDate().toString());
                writeNullable(output, row.motherIdentification());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void writeNullable(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) output.writeUTF(value);
    }
}
