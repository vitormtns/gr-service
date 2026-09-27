package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.gerenciadorrural.modules.herd.application.ImportCurrentFarmAnimals;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HerdAnimalImportRequestDeserializer extends StdDeserializer<HerdAnimalImportController.ImportRequest> {
    private static final Set<String> ROOT = Set.of("operationId", "animals");
    private static final Set<String> ROW = Set.of("id", "identification", "name", "sex", "status",
            "birthDate", "motherIdentification");

    public HerdAnimalImportRequestDeserializer() {
        super(HerdAnimalImportController.ImportRequest.class);
    }

    @Override
    public HerdAnimalImportController.ImportRequest deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        JsonNode root = parser.getCodec().readTree(parser);
        fields(parser, root, ROOT);
        if (parser.nextToken() != null) throw JsonMappingException.from(parser, "O comando contém conteúdo adicional");
        JsonNode animals = root.get("animals");
        if (animals == null || !animals.isArray() || animals.isEmpty() || animals.size() > 100)
            throw JsonMappingException.from(parser, "A lista de animais é inválida");
        List<ImportCurrentFarmAnimals.Row> rows = new ArrayList<>();
        for (JsonNode item : animals) {
            fields(parser, item, ROW);
            rows.add(new ImportCurrentFarmAnimals.Row(uuid(parser, item, "id"),
                    string(parser, item, "identification"), string(parser, item, "name"),
                    enumeration(parser, item, "sex", HerdAnimalSex.class),
                    enumeration(parser, item, "status", HerdAnimalStatus.class),
                    date(parser, item, "birthDate"), string(parser, item, "motherIdentification")));
        }
        return new HerdAnimalImportController.ImportRequest(uuid(parser, root, "operationId"), List.copyOf(rows));
    }

    private static void fields(JsonParser parser, JsonNode node, Set<String> allowed) throws JsonMappingException {
        if (node == null || !node.isObject()) throw JsonMappingException.from(parser, "O item deve ser um objeto JSON");
        var names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next()))
                throw JsonMappingException.from(parser, "A propriedade enviada não é permitida");
        }
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
        catch (IllegalArgumentException error) { throw JsonMappingException.from(parser, "O UUID é inválido", error); }
    }

    private static LocalDate date(JsonParser parser, JsonNode node, String field) throws JsonMappingException {
        String value = string(parser, node, field);
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException error) { throw JsonMappingException.from(parser, "A data é inválida", error); }
    }

    private static <E extends Enum<E>> E enumeration(JsonParser parser, JsonNode node, String field,
                                                      Class<E> type) throws JsonMappingException {
        String value = string(parser, node, field);
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException error) { throw JsonMappingException.from(parser, "O valor de " + field + " é inválido", error); }
    }
}
