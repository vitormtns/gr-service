package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.gerenciadorrural.modules.herd.domain.HerdPlannerType;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public final class HerdPlannerRequestDeserializer
    extends StdDeserializer<HerdPlannerController.PlannerRequest> {

  private static final Set<String> FIELDS =
      Set.of(
          "operationId", "expectedVersion", "type", "title", "notes", "scheduledFor", "animalId");

  public HerdPlannerRequestDeserializer() {
    super(HerdPlannerController.PlannerRequest.class);
  }

  @Override
  public HerdPlannerController.PlannerRequest deserialize(
      JsonParser parser, DeserializationContext context) throws IOException {
    try {
      parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
      JsonNode node = parser.getCodec().readTree(parser);
      if (!node.isObject()) {
        throw new IllegalArgumentException();
      }
      for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
        if (!FIELDS.contains(names.next())) {
          throw new IllegalArgumentException();
        }
      }
      return new HerdPlannerController.PlannerRequest(
          uuid(node, "operationId", true),
          number(node, "expectedVersion"),
          HerdPlannerType.valueOf(text(node, "type", true)),
          text(node, "title", true),
          text(node, "notes", false),
          date(node, "scheduledFor", true),
          uuid(node, "animalId", false));
    } catch (Exception exception) {
      throw JsonMappingException.from(parser, "O comando do planejador é inválido", exception);
    }
  }

  static UUID uuid(JsonNode node, String field, boolean required) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      if (required) {
        throw new IllegalArgumentException();
      }
      return null;
    }
    if (!value.isTextual()) {
      throw new IllegalArgumentException();
    }
    return UUID.fromString(value.textValue());
  }

  static Long number(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new IllegalArgumentException();
    }
    return value.longValue();
  }

  static String text(JsonNode node, String field, boolean required) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      if (required) {
        throw new IllegalArgumentException();
      }
      return null;
    }
    if (!value.isTextual()) {
      throw new IllegalArgumentException();
    }
    return value.textValue();
  }

  static LocalDate date(JsonNode node, String field, boolean required) {
    String value = text(node, field, required);
    return value == null ? null : LocalDate.parse(value);
  }
}
