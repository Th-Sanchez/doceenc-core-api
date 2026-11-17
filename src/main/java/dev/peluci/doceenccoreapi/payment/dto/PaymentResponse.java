package dev.peluci.doceenccoreapi.payment.dto;

import dev.peluci.doceenccoreapi.payment.OrderPayment;
import dev.peluci.doceenccoreapi.payment.PaymentMethod;
import dev.peluci.doceenccoreapi.payment.PaymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record PaymentResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) PaymentType type,
        @Schema(requiredMode = REQUIRED) BigDecimal amount,
        @Schema(requiredMode = REQUIRED) PaymentMethod method,
        @Schema(requiredMode = REQUIRED) LocalDate paidOn,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant voidedAt,
        @Schema(requiredMode = REQUIRED, nullable = true) String notes) {

    public static PaymentResponse from(OrderPayment payment) {
        return new PaymentResponse(payment.getId(), payment.getType(), payment.getAmount(), payment.getMethod(),
                payment.getPaidOn(), payment.getVoidedAt(), payment.getNotes());
    }
}
