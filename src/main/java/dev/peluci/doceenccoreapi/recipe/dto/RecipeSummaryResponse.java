package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import dev.peluci.doceenccoreapi.recipe.RecipeCostCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record RecipeSummaryResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) RecipeCategoryResponse category,
        @Schema(requiredMode = REQUIRED) BigDecimal yieldQuantity,
        @Schema(requiredMode = REQUIRED) BaseUnit yieldUnit,
        @Schema(requiredMode = REQUIRED, nullable = true) Integer prepTimeMinutes,
        @Schema(requiredMode = REQUIRED, nullable = true) Integer shelfLifeDays,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) int itemCount,
        @Schema(requiredMode = REQUIRED) BigDecimal totalCost,
        @Schema(requiredMode = REQUIRED) BigDecimal costPerYieldUnit,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {

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
