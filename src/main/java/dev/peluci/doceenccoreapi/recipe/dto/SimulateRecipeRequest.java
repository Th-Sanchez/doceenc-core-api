package dev.peluci.doceenccoreapi.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record SimulateRecipeRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal yieldQuantity,
        @NotNull @Valid List<RecipeItemRequest> items) {
}
