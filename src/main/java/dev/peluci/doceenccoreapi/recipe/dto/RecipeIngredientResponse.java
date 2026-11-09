package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record RecipeIngredientResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED, nullable = true) String brandSupplier,
        @Schema(requiredMode = REQUIRED) BaseUnit baseUnit,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) BigDecimal unitPrice) {

    static RecipeIngredientResponse from(Ingredient ingredient) {
        return new RecipeIngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getBrandSupplier(),
                ingredient.getBaseUnit(),
                ingredient.isActive(),
                ingredient.getPrice().getUnitPrice());
    }

}
