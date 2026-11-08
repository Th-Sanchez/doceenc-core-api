package dev.peluci.doceenccoreapi.recipe.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecipeSimulationResponse(
        List<RecipeItemResponse> items,
        BigDecimal totalCost,
        BigDecimal costPerYieldUnit) {
}
