package dev.peluci.doceenccoreapi.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record RecipeSimulationResponse(
        @Schema(requiredMode = REQUIRED) List<RecipeItemResponse> items,
        @Schema(requiredMode = REQUIRED) BigDecimal totalCost,
        @Schema(requiredMode = REQUIRED) BigDecimal costPerYieldUnit) {
}
