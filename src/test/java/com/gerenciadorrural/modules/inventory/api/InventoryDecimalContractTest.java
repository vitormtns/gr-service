package com.gerenciadorrural.modules.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryDecimalContractTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test void preservesAllNineteenSignificantDigitsBeforeStockCalculation() throws Exception {
        var request = mapper.readValue(command("9007199254740.123456"), InventoryController.MovementRequest.class);
        assertThat(request.quantity).isEqualByComparingTo("9007199254740.123456");
    }

    @Test void rejectsExcessFractionalDigitsEvenWhenFloatingPointWouldRoundThemAway() {
        assertThatThrownBy(() -> mapper.readValue(command("1.00000000000000001"), InventoryController.MovementRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test void rejectsQuantityOutsideNumericNineteenSixBeforePersistence() {
        assertThatThrownBy(() -> mapper.readValue(command("10000000000000"), InventoryController.MovementRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test void preservesScaleSoExcessTrailingZeroesCannotBypassTheContract() {
        assertThatThrownBy(() -> mapper.readValue(command("1.0000000"), InventoryController.MovementRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    private String command(String quantity) {
        return """
                {"operationId":"00000000-0000-4000-8000-000000000001","type":"RECEIPT",
                 "productId":"00000000-0000-4000-8000-000000000002",
                 "destinationLocationId":"00000000-0000-4000-8000-000000000003",
                 "quantity":%s,"occurredOn":"2026-09-28"}
                """.formatted(quantity);
    }
}
