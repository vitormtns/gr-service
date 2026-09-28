package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.gerenciadorrural.modules.herd.application.BatchBreedCurrentFarmAnimals;
import com.gerenciadorrural.modules.herd.domain.ReproductionServiceType;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HerdBreedingBatchRequestDeserializer
        extends StdDeserializer<HerdBreedingBatchController.Request> {
    private static final Set<String> ROOT = Set.of("operationId", "serviceType", "serviceOn",
            "sireReference", "expectedCalvingOn", "notes", "mothers");
    private static final Set<String> MOTHER = Set.of("id", "expectedVersion");

    public HerdBreedingBatchRequestDeserializer() {
        super(HerdBreedingBatchController.Request.class);
    }

    @Override
    public HerdBreedingBatchController.Request deserialize(JsonParser parser,
            DeserializationContext context) throws IOException {
        parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        JsonNode root = parser.getCodec().readTree(parser);
        fields(parser, root, ROOT);
        if (parser.nextToken() != null)
            throw JsonMappingException.from(parser, "O comando contém conteúdo adicional");
        JsonNode mothers = root.get("mothers");
        if (mothers == null || !mothers.isArray() || mothers.isEmpty() || mothers.size() > 100)
            throw JsonMappingException.from(parser, "A lista de mães é inválida");
        List<BatchBreedCurrentFarmAnimals.Mother> items = new ArrayList<>();
        for (JsonNode mother : mothers) {
            fields(parser, mother, MOTHER);
            JsonNode version = mother.get("expectedVersion");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToLong())
                throw JsonMappingException.from(parser, "A versão esperada é inválida");
            items.add(new BatchBreedCurrentFarmAnimals.Mother(uuid(parser, mother, "id"),
                    version.longValue()));
        }
        String type = string(parser, root, "serviceType");
        ReproductionServiceType serviceType = null;
        if (type != null) {
            try { serviceType = ReproductionServiceType.valueOf(type); }
            catch (IllegalArgumentException error) {
                throw JsonMappingException.from(parser, "O tipo de reprodução é inválido", error);
            }
        }
        return new HerdBreedingBatchController.Request(uuid(parser, root, "operationId"),
                serviceType, date(parser, root, "serviceOn"), string(parser, root, "sireReference"),
                date(parser, root, "expectedCalvingOn"), string(parser, root, "notes"),
                List.copyOf(items));
    }

    private static void fields(JsonParser parser, JsonNode node, Set<String> allowed)
            throws JsonMappingException {
        if (node == null || !node.isObject())
            throw JsonMappingException.from(parser, "O item deve ser um objeto JSON");
        var fields = node.fieldNames();
        while (fields.hasNext())
            if (!allowed.contains(fields.next()))
                throw JsonMappingException.from(parser, "A propriedade enviada não é permitida");
    }

    private static String string(JsonParser parser, JsonNode node, String field) throws JsonMappingException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw JsonMappingException.from(parser, "O campo " + field + " deve ser texto");
        return value.textValue();
    }

    private static UUID uuid(JsonParser parser, JsonNode node, String field) throws JsonMappingException {
        String value = string(parser, node, field);
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException error) {
            throw JsonMappingException.from(parser, "O UUID é inválido", error);
        }
    }

    private static LocalDate date(JsonParser parser, JsonNode node, String field) throws JsonMappingException {
        String value = string(parser, node, field);
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException error) {
            throw JsonMappingException.from(parser, "A data é inválida", error);
        }
    }
}
