package dev.peluci.doceenccoreapi.ingredient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateIngredientRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String brandSupplier,
        @NotNull UUID categoryId) {
}
