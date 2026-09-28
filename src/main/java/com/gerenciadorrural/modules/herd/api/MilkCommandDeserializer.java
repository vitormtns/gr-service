package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.gerenciadorrural.modules.herd.domain.MilkSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class MilkCommandDeserializer extends StdDeserializer<MilkController.MilkCommand> {
  public MilkCommandDeserializer() {
    super(MilkController.MilkCommand.class);
  }

  @Override
  public MilkController.MilkCommand deserialize(JsonParser parser,
      DeserializationContext context) throws IOException {
    parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      throw JsonMappingException.from(parser, "O comando de leite deve ser um objeto JSON");
    }
    UUID operationId = null;
    Long expectedVersion = null;
    LocalDate recordedOn = null;
    BigDecimal liters = null;
    MilkSession session = null;
    String notes = null;
    try {
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.currentToken() != JsonToken.FIELD_NAME) {
          throw new IllegalArgumentException();
        }
        String field = parser.currentName();
        parser.nextToken();
        switch (field) {
          case "operationId" -> operationId = UUID.fromString(text(parser));
          case "expectedVersion" -> {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
              throw new IllegalArgumentException();
            }
            expectedVersion = parser.getLongValue();
          }
          case "recordedOn" -> recordedOn = LocalDate.parse(text(parser));
          case "liters" -> {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT) {
              throw new IllegalArgumentException();
            }
            liters = parser.getDecimalValue();
          }
          case "session" -> {
            String value = optionalText(parser);
            session = value == null ? null : MilkSession.valueOf(value);
          }
          case "notes" -> notes = optionalText(parser);
          default -> throw new IllegalArgumentException();
        }
      }
      if (operationId == null || expectedVersion == null || recordedOn == null || liters == null) {
        throw new IllegalArgumentException();
      }
      return new MilkController.MilkCommand(operationId, expectedVersion, recordedOn,
          liters, session, notes);
    } catch (RuntimeException error) {
      throw JsonMappingException.from(parser, "O comando de leite é inválido", error);
    }
  }

  private static String text(JsonParser parser) throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_STRING) {
      throw new IllegalArgumentException();
    }
    return parser.getText();
  }

  private static String optionalText(JsonParser parser) throws IOException {
    return parser.currentToken() == JsonToken.VALUE_NULL ? null : text(parser);
  }
}
