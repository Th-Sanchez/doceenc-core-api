package dev.peluci.doceenccoreapi.order.dto;

import dev.peluci.doceenccoreapi.payment.dto.PaymentRequest;
import jakarta.validation.Valid;

public record ConfirmRequest(@Valid PaymentRequest initialPayment) {
}
