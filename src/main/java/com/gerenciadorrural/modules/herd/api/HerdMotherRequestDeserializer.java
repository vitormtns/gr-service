package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public final class HerdMotherRequestDeserializer extends StdDeserializer<HerdMotherController.Request> {
    private static final Set<String> ALLOWED = Set.of("operationId", "expectedVersion", "motherId");

    public HerdMotherRequestDeserializer() {
        super(HerdMotherController.Request.class);
    }

    @Override
    public HerdMotherController.Request deserialize(JsonParser parser, DeserializationContext context)
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
        return new HerdMotherController.Request(uuid(parser, body, "operationId"),
                version.longValue(), uuid(parser, body, "motherId"));
    }

    private static UUID uuid(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw JsonMappingException.from(parser, "O UUID é inválido");
        try { return UUID.fromString(value.textValue()); }
        catch (IllegalArgumentException error) { throw JsonMappingException.from(parser, "O UUID é inválido", error); }
    }
}
