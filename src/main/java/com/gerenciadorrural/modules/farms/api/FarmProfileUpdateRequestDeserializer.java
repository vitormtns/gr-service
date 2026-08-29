package com.gerenciadorrural.modules.farms.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

public final class FarmProfileUpdateRequestDeserializer extends StdDeserializer<FarmProfileController.UpdateRequest> {

    private static final Set<String> ALLOWED_FIELDS = Set.of("name", "expectedVersion");

    public FarmProfileUpdateRequestDeserializer() {
        super(FarmProfileController.UpdateRequest.class);
    }

    @Override
    public FarmProfileController.UpdateRequest deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        JsonNode body = parser.getCodec().readTree(parser);
        if (!body.isObject()) {
            throw JsonMappingException.from(parser, "O corpo de atualização deve ser um objeto JSON");
        }
        JsonToken trailingToken = parser.nextToken();
        if (trailingToken != null) {
            throw JsonMappingException.from(parser, "O corpo de atualização contém conteúdo adicional");
        }
        Iterator<String> fields = body.fieldNames();
        while (fields.hasNext()) {
            if (!ALLOWED_FIELDS.contains(fields.next())) {
                throw JsonMappingException.from(parser, "A propriedade enviada não é permitida");
            }
        }
        JsonNode name = body.get("name");
        JsonNode expectedVersion = body.get("expectedVersion");
        if (name != null && !name.isNull() && !name.isTextual()) {
            throw JsonMappingException.from(parser, "O nome deve ser texto");
        }
        if (expectedVersion != null && !expectedVersion.isNull()
                && (!expectedVersion.isIntegralNumber() || !expectedVersion.canConvertToLong())) {
            throw JsonMappingException.from(parser, "A versão esperada deve ser um número inteiro");
        }
        return new FarmProfileController.UpdateRequest(
                name == null || name.isNull() ? null : name.textValue(),
                expectedVersion == null || expectedVersion.isNull() ? null : expectedVersion.longValue()
        );
    }
}
