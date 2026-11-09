package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.recipe.RecipeCostCalculator;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Comparator;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record RecipeItemResponse(
        @Schema(requiredMode = REQUIRED) RecipeIngredientResponse ingredient,
        @Schema(requiredMode = REQUIRED) BigDecimal quantity,
        @Schema(requiredMode = REQUIRED) BigDecimal cost) {

    public static RecipeItemResponse of(Ingredient ingredient, BigDecimal quantity) {
        BigDecimal normalized = Scale.quantity(quantity);
        BigDecimal cost = RecipeCostCalculator.itemCost(normalized, ingredient.getPrice().getUnitPrice());

        return new RecipeItemResponse(RecipeIngredientResponse.from(ingredient), normalized, cost);
    }

    public static Comparator<RecipeItemResponse> byIngredientName() {
        return Comparator.comparing(item -> item.ingredient().name(),
                String.CASE_INSENSITIVE_ORDER);
    }

}
