package com.gerenciadorrural.modules.herd.application;

import com.gerenciadorrural.modules.herd.domain.HerdBreedingBatchRepository;
import com.gerenciadorrural.modules.herd.domain.HerdBreedingBatchRepository.Receipt;
import com.gerenciadorrural.modules.herd.domain.ReproductionServiceType;
import com.gerenciadorrural.modules.herd.domain.ReproductionPolicy;
import com.gerenciadorrural.shared.tenancy.TenantContext;
import com.gerenciadorrural.shared.tenancy.TenantTransactionExecutor;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BatchBreedCurrentFarmAnimals {
    public record Mother(UUID id, Long expectedVersion) {}
    public record Item(UUID motherId, UUID pregnancyId) {}
    public record Result(List<Item> items, boolean replayed) {}

    private static final Set<String> WRITE = Set.of("OWNER", "ADMIN", "MANAGER", "OPERATOR");
    private final TenantTransactionExecutor transactions;
    private final ManageHerdReproduction reproduction;
    private final HerdBreedingBatchRepository batches;

    public BatchBreedCurrentFarmAnimals(TenantTransactionExecutor transactions,
            ManageHerdReproduction reproduction, HerdBreedingBatchRepository batches) {
        this.transactions = transactions;
        this.reproduction = reproduction;
        this.batches = batches;
    }

    public Result execute(TenantContext context, UUID operationId, ReproductionServiceType serviceType,
            LocalDate serviceOn, String sireReference, LocalDate expectedCalvingOn,
            String notes, List<Mother> input) {
        Objects.requireNonNull(context);
        if (!WRITE.contains(context.role())) throw new HerdMovementForbiddenException();
        if (operationId == null || serviceType == null || serviceOn == null
                || input == null || input.isEmpty() || input.size() > 100)
            throw new HerdAnimalCommandInvalidException();
        String sire = normalize(sireReference, 160);
        String note = normalize(notes, 1000);
        Set<UUID> ids = new HashSet<>();
        for (Mother mother : input)
            if (mother == null || mother.id() == null || mother.expectedVersion() == null
                    || mother.expectedVersion() < 0 || !ids.add(mother.id()))
                throw new HerdAnimalCommandInvalidException();
        List<Mother> mothers = input.stream().sorted(Comparator.comparing(Mother::id)).toList();
        String hash = hash(serviceType, serviceOn, sire, expectedCalvingOn, note, mothers);
        return transactions.execute(context, () -> {
            batches.lock(context.tenantId(), context.farmId(), operationId);
            var previous = batches.find(context.tenantId(), context.farmId(), operationId);
            if (previous.isPresent()) {
                if (!previous.get().payloadHash().equals(hash))
                    throw new HerdOperationIdempotencyConflictException();
                return result(previous.get(), true);
            }
            if (expectedCalvingOn != null
                    && !expectedCalvingOn.equals(ReproductionPolicy.expectedCalvingOn(serviceOn)))
                throw new HerdAnimalCommandInvalidException();
            List<UUID> motherIds = new ArrayList<>();
            List<UUID> pregnancyIds = new ArrayList<>();
            for (Mother mother : mothers) {
                UUID childOperation = UUID.nameUUIDFromBytes(("herd-breeding-batch:"
                        + operationId + ":" + mother.id()).getBytes(StandardCharsets.UTF_8));
                var pregnancy = reproduction.breed(context, mother.id(),
                        new ManageHerdReproduction.Breeding(childOperation, mother.expectedVersion(),
                                serviceType, serviceOn, sire, expectedCalvingOn, note));
                motherIds.add(mother.id());
                pregnancyIds.add(pregnancy.id());
            }
            Receipt receipt = new Receipt(hash, List.copyOf(motherIds), List.copyOf(pregnancyIds));
            batches.save(context.tenantId(), context.farmId(), operationId, receipt);
            return result(receipt, false);
        });
    }

    private static Result result(Receipt receipt, boolean replayed) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < receipt.motherIds().size(); i++)
            items.add(new Item(receipt.motherIds().get(i), receipt.pregnancyIds().get(i)));
        return new Result(List.copyOf(items), replayed);
    }

    private static String normalize(String text, int max) {
        if (text == null) return null;
        String value = text.strip();
        if (value.isEmpty() || value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > max)
            throw new HerdAnimalCommandInvalidException();
        return value;
    }

    private static String hash(ReproductionServiceType type, LocalDate date, String sire,
            LocalDate expected, String notes, List<Mother> mothers) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF(type.name());
            output.writeUTF(date.toString());
            nullable(output, sire);
            nullable(output, expected == null ? null : expected.toString());
            nullable(output, notes);
            output.writeInt(mothers.size());
            for (Mother mother : mothers) {
                output.writeUTF(mother.id().toString());
                output.writeLong(mother.expectedVersion());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void nullable(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) output.writeUTF(value);
    }
}
