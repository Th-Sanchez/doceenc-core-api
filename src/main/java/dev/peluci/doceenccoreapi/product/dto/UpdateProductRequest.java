package dev.peluci.doceenccoreapi.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID recipeId,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal recipeQuantity,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal retailPrice,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal wholesalePrice,
        @NotNull @Valid List<ProductItemRequest> items) {
}
