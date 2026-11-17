package dev.peluci.doceenccoreapi.order.dto;

import dev.peluci.doceenccoreapi.payment.PaymentMethod;

public record CancelRequest(PaymentMethod refundMethod) {
}
