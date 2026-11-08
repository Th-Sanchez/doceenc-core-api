package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import dev.peluci.doceenccoreapi.recipe.RecipeCostCalculator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecipeSummaryResponse(
        UUID id,
        String name,
        RecipeCategoryResponse category,
        BigDecimal yieldQuantity,
        BaseUnit yieldUnit,
        Integer prepTimeMinutes,
        Integer shelfLifeDays,
        boolean active,
        int itemCount,
        BigDecimal totalCost,
        BigDecimal costPerYieldUnit,
        Instant createdAt,
        Instant updatedAt) {

    public static RecipeSummaryResponse from(Recipe recipe) {
        RecipeCostCalculator.Summary cost = recipe.cost();
        return new RecipeSummaryResponse(
                recipe.getId(),
                recipe.getName(),
                RecipeCategoryResponse.from(recipe.getCategory()),
                recipe.getYieldQuantity(),
                recipe.getYieldUnit(),
                recipe.getPrepTimeMinutes(),
                recipe.getShelfLifeDays(),
                recipe.isActive(),
                recipe.getItems().size(),
                cost.totalCost(),
                cost.costPerYieldUnit(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt());
    }

}
