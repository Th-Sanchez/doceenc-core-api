package dev.peluci.doceenccoreapi.order.dto;

import dev.peluci.doceenccoreapi.order.LineIssue;
import dev.peluci.doceenccoreapi.order.PriceChannel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record OrderSimulationResponse(
        @Schema(requiredMode = REQUIRED) PriceChannel priceChannel,
        @Schema(requiredMode = REQUIRED) List<ItemResponse> items,
        @Schema(requiredMode = REQUIRED) BigDecimal totalAmount) {

    public record ItemResponse(
            @Schema(requiredMode = REQUIRED) UUID productId,
            @Schema(requiredMode = REQUIRED, nullable = true) String productName,
            @Schema(requiredMode = REQUIRED) int quantity,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal unitPrice,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal unitCost,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal subtotal,
            @Schema(requiredMode = REQUIRED, nullable = true) LineIssue issue
    ) {
    }

}
