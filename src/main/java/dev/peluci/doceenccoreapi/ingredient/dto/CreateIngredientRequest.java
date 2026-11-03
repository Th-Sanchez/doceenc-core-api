package dev.peluci.doceenccoreapi.ingredient.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateIngredientRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String brandSupplier,
        @NotNull UUID categoryId,
        @NotNull BaseUnit baseUnit,
        @NotNull @Valid PriceRequest price) {
}
