package dev.peluci.doceenccoreapi.ingredient.dto;

import dev.peluci.doceenccoreapi.ingredient.IngredientPrice;
import dev.peluci.doceenccoreapi.ingredient.IngredientPriceHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record PriceHistoryResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) BigDecimal packagePrice,
        @Schema(requiredMode = REQUIRED) BigDecimal packageQuantity,
        @Schema(requiredMode = REQUIRED) BigDecimal unitPrice,
        @Schema(requiredMode = REQUIRED) LocalDate purchasedOn,
        @Schema(requiredMode = REQUIRED) Instant createdAt) {

    public static PriceHistoryResponse from(IngredientPriceHistory h) {
        IngredientPrice p = h.getPrice();
        return new PriceHistoryResponse(h.getId(), p.getPackagePrice(), p.getPackageQuantity(),
                p.getUnitPrice(), h.getPurchasedOn(), h.getCreatedAt());
    }

}
