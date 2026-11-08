package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeIngredientResponse(
        UUID id,
        String name,
        String brandSupplier,
        BaseUnit baseUnit,
        boolean active,
        BigDecimal unitPrice) {

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
