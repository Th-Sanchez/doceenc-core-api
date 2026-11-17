package dev.peluci.doceenccoreapi.payment.dto;

import dev.peluci.doceenccoreapi.payment.PaymentMethod;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRequest(
        @NotNull @Positive @Digits(integer = 12, fraction = Scale.MONEY) BigDecimal amount,
        @NotNull PaymentMethod method,
        LocalDate paidOn,
        @Size(max = 1000) String notes) {
}
