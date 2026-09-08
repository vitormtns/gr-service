package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Enforces the create-command boundary independently from global Jackson leniency. */
public final class HerdAnimalCreateRequestDeserializer extends StdDeserializer<HerdAnimalController.CreateRequest> {

    private static final Set<String> ALLOWED_FIELDS = Set.of("id", "identification", "name", "sex", "birthDate");

    public HerdAnimalCreateRequestDeserializer() {
        super(HerdAnimalController.CreateRequest.class);
    }

    @Override
    public HerdAnimalController.CreateRequest deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        JsonNode body = parser.getCodec().readTree(parser);
        if (!body.isObject()) {
            throw JsonMappingException.from(parser, "O comando deve ser um objeto JSON");
        }
        if (parser.nextToken() != null) {
            throw JsonMappingException.from(parser, "O comando contém conteúdo adicional");
        }
        Iterator<String> fields = body.fieldNames();
        while (fields.hasNext()) {
            if (!ALLOWED_FIELDS.contains(fields.next())) {
                throw JsonMappingException.from(parser, "A propriedade enviada não é permitida");
            }
        }
        return new HerdAnimalController.CreateRequest(
                uuid(parser, body, "id"),
                text(parser, body, "identification"),
                text(parser, body, "name"),
                sex(parser, body),
                birthDate(parser, body)
        );
    }

    private static UUID uuid(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        String value = text(parser, body, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw JsonMappingException.from(parser, "O id deve ser um UUID válido", exception);
        }
    }

    private static HerdAnimalSex sex(JsonParser parser, JsonNode body) throws JsonMappingException {
        String value = text(parser, body, "sex");
        if (value == null) {
            return null;
        }
        try {
            return HerdAnimalSex.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw JsonMappingException.from(parser, "O sexo informado é inválido", exception);
        }
    }

    private static LocalDate birthDate(JsonParser parser, JsonNode body) throws JsonMappingException {
        String value = text(parser, body, "birthDate");
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw JsonMappingException.from(parser, "A data de nascimento é inválida", exception);
        }
    }

    private static String text(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw JsonMappingException.from(parser, field + " deve ser texto");
        }
        return value.textValue();
    }

}
