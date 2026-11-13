package dev.peluci.doceenccoreapi.settings;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateSettingsRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal targetMarginRetail,
        @NotNull @DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal targetMarginWholesale) {
}
