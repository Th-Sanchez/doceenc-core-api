package dev.peluci.doceenccoreapi.product.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.recipe.RecipeCostCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ProductItemResponse(
        @Schema(requiredMode = REQUIRED) UUID ingredientId,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) BaseUnit baseUnit,
        @Schema(requiredMode = REQUIRED) boolean ingredientActive,
        @Schema(requiredMode = REQUIRED) BigDecimal quantity,
        @Schema(requiredMode = REQUIRED) BigDecimal unitPrice,
        @Schema(requiredMode = REQUIRED) BigDecimal cost) {

    public static ProductItemResponse of(Ingredient ingredient, BigDecimal quantity) {
        BigDecimal unitPrice = ingredient.getPrice().getUnitPrice();
        return new ProductItemResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getBaseUnit(),
                ingredient.isActive(),
                quantity,
                unitPrice,
                RecipeCostCalculator.itemCost(quantity, unitPrice));
    }

    public static Comparator<ProductItemResponse> byIngredientName() {
        return Comparator.comparing(ProductItemResponse::name, String.CASE_INSENSITIVE_ORDER);
    }

}
