package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import dev.peluci.doceenccoreapi.recipe.RecipeCostCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record RecipeDetailResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) RecipeCategoryResponse category,
        @Schema(requiredMode = REQUIRED) BigDecimal yieldQuantity,
        @Schema(requiredMode = REQUIRED) BaseUnit yieldUnit,
        @Schema(requiredMode = REQUIRED, nullable = true) Integer prepTimeMinutes,
        @Schema(requiredMode = REQUIRED, nullable = true) Integer shelfLifeDays,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) List<RecipeItemResponse> items,
        @Schema(requiredMode = REQUIRED) BigDecimal totalCost,
        @Schema(requiredMode = REQUIRED) BigDecimal costPerYieldUnit,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {

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
