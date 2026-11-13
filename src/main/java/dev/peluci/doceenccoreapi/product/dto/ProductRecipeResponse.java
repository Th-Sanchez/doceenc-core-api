package dev.peluci.doceenccoreapi.product.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ProductRecipeResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) BigDecimal yieldQuantity,
        @Schema(requiredMode = REQUIRED) BaseUnit yieldUnit,
        @Schema(requiredMode = REQUIRED) BigDecimal costPerYieldUnit) {

    public static ProductRecipeResponse from(Recipe recipe) {
        return new ProductRecipeResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.isActive(),
                recipe.getYieldQuantity(),
                recipe.getYieldUnit(),
                recipe.cost().costPerYieldUnit());
    }

}
