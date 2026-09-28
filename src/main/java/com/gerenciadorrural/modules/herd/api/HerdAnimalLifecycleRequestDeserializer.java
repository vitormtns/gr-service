package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.gerenciadorrural.modules.herd.domain.SaleChannel;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class HerdAnimalLifecycleRequestDeserializer
    extends StdDeserializer<HerdAnimalController.LifecycleRequest> {
  public HerdAnimalLifecycleRequestDeserializer() {
    super(HerdAnimalController.LifecycleRequest.class);
  }

  @Override
  public HerdAnimalController.LifecycleRequest deserialize(JsonParser parser,
      DeserializationContext context) throws IOException {
    parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      throw JsonMappingException.from(parser, "O comando deve ser um objeto JSON");
    }
    UUID operationId = null;
    Long expectedVersion = null;
    LocalDate occurredOn = null;
    String notes = null;
    String deathReason = null;
    SaleChannel saleChannel = null;
    String saleBuyer = null;
    BigDecimal saleAmount = null;
    try {
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.currentToken() != JsonToken.FIELD_NAME) {
          throw new IllegalArgumentException();
        }
        String field = parser.currentName();
        parser.nextToken();
        switch (field) {
          case "operationId" -> operationId = UUID.fromString(requiredText(parser));
          case "expectedVersion" -> {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
              throw new IllegalArgumentException();
            }
            expectedVersion = parser.getLongValue();
          }
          case "occurredOn" -> occurredOn = LocalDate.parse(requiredText(parser));
          case "notes" -> notes = optionalText(parser);
          case "deathReason" -> deathReason = optionalText(parser);
          case "saleChannel" -> {
            String value = optionalText(parser);
            saleChannel = value == null ? null : SaleChannel.valueOf(value);
          }
          case "saleBuyer" -> saleBuyer = optionalText(parser);
          case "saleAmount" -> {
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
              saleAmount = null;
            } else if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
              saleAmount = parser.getDecimalValue();
            } else {
              throw new IllegalArgumentException();
            }
          }
          default -> throw new IllegalArgumentException();
        }
      }
      if (operationId == null || expectedVersion == null || occurredOn == null) {
        throw new IllegalArgumentException();
      }
      return new HerdAnimalController.LifecycleRequest(operationId, expectedVersion,
          occurredOn, notes, deathReason, saleChannel, saleBuyer, saleAmount);
    } catch (RuntimeException error) {
      throw JsonMappingException.from(parser, "O comando de ciclo de vida é inválido", error);
    }
  }

  private static String requiredText(JsonParser parser) throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_STRING) {
      throw new IllegalArgumentException();
    }
    return parser.getText();
  }

  private static String optionalText(JsonParser parser) throws IOException {
    return parser.currentToken() == JsonToken.VALUE_NULL ? null : requiredText(parser);
  }
}
