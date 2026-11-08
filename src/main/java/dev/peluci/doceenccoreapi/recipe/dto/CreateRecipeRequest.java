package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateRecipeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID categoryId,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal yieldQuantity,
        @NotNull BaseUnit yieldUnit,
        @Positive Integer prepTimeMinutes,
        @Positive Integer shelfLifeDays,
        @NotEmpty @Valid List<RecipeItemRequest> items) {
}
