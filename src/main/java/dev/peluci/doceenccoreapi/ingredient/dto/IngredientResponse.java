package dev.peluci.doceenccoreapi.ingredient.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.ingredient.IngredientPrice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record IngredientResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED, nullable = true) String brandSupplier,
        @Schema(requiredMode = REQUIRED) CategorySummary category,
        @Schema(requiredMode = REQUIRED) BaseUnit baseUnit,
        @Schema(requiredMode = REQUIRED) BigDecimal packagePrice,
        @Schema(requiredMode = REQUIRED) BigDecimal packageQuantity,
        @Schema(requiredMode = REQUIRED) BigDecimal unitPrice,
        @Schema(requiredMode = REQUIRED, nullable = true) LocalDate lastPurchasedOn,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal variationPercent,
        @Schema(requiredMode = REQUIRED) boolean active) {

    public record CategorySummary(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED) String name) {
    }

    public static IngredientResponse from(Ingredient i, LocalDate lastPurchasedOn, BigDecimal variationPercent) {
        IngredientPrice p = i.getPrice();
        return new IngredientResponse(i.getId(), i.getName(), i.getBrandSupplier(),
                new CategorySummary(i.getCategory().getId(), i.getCategory().getName()),
                i.getBaseUnit(), p.getPackagePrice(), p.getPackageQuantity(), p.getUnitPrice(),
                lastPurchasedOn, variationPercent, i.isActive());
    }
}
