package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class HerdAnimalNoteRequestDeserializer extends StdDeserializer<HerdAnimalNoteController.Request> {
    private static final Set<String> ALLOWED = Set.of("operationId", "expectedVersion", "occurredOn", "notes");

    public HerdAnimalNoteRequestDeserializer() {
        super(HerdAnimalNoteController.Request.class);
    }

    @Override
    public HerdAnimalNoteController.Request deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        JsonNode body = parser.getCodec().readTree(parser);
        if (body == null || !body.isObject())
            throw JsonMappingException.from(parser, "O comando deve ser um objeto JSON");
        var fields = body.fieldNames();
        while (fields.hasNext())
            if (!ALLOWED.contains(fields.next()))
                throw JsonMappingException.from(parser, "A propriedade enviada não é permitida");
        if (parser.nextToken() != null)
            throw JsonMappingException.from(parser, "O comando contém conteúdo adicional");
        JsonNode version = body.get("expectedVersion");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToLong())
            throw JsonMappingException.from(parser, "A versão esperada é inválida");
        return new HerdAnimalNoteController.Request(uuid(parser, body, "operationId"),
                version.longValue(), date(parser, body, "occurredOn"), string(parser, body, "notes"));
    }

    private static UUID uuid(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        String value = string(parser, body, field);
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException error) { throw JsonMappingException.from(parser, "O UUID é inválido", error); }
    }

    private static LocalDate date(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        String value = string(parser, body, field);
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException error) { throw JsonMappingException.from(parser, "A data é inválida", error); }
    }

    private static String string(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw JsonMappingException.from(parser, "O campo " + field + " deve ser texto");
        return value.textValue();
    }
}
