package dev.peluci.doceenccoreapi.ingredient.dto;

import dev.peluci.doceenccoreapi.ingredient.InputUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 5, fraction = 2) BigDecimal packagePrice,
        @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 4) BigDecimal packageQuantity,
        @NotNull InputUnit unit,
        LocalDate purchasedOn) {
}
