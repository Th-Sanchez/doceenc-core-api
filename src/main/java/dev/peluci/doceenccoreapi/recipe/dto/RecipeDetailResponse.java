package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import dev.peluci.doceenccoreapi.recipe.RecipeCostCalculator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecipeDetailResponse(
        UUID id,
        String name,
        RecipeCategoryResponse category,
        BigDecimal yieldQuantity,
        BaseUnit yieldUnit,
        Integer prepTimeMinutes,
        Integer shelfLifeDays,
        boolean active,
        List<RecipeItemResponse> items,
        BigDecimal totalCost,
        BigDecimal costPerYieldUnit,
        Instant createdAt,
        Instant updatedAt) {

    public static RecipeDetailResponse from(Recipe recipe) {
        RecipeCostCalculator.Summary cost = recipe.cost();
        List<RecipeItemResponse> items = recipe.getItems().stream()
                .map(item -> RecipeItemResponse.of(item.getIngredient(), item.getQuantity()))
                .sorted(RecipeItemResponse.byIngredientName())
                .toList();
        return new RecipeDetailResponse(
                recipe.getId(),
                recipe.getName(),
                RecipeCategoryResponse.from(recipe.getCategory()),
                recipe.getYieldQuantity(),
                recipe.getYieldUnit(),
                recipe.getPrepTimeMinutes(),
                recipe.getShelfLifeDays(),
                recipe.isActive(),
                items,
                cost.totalCost(),
                cost.costPerYieldUnit(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt());
    }
}
