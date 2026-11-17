package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.payment.PaymentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderFilter(List<OrderStatus> statuses, LocalDate deliveryFrom, LocalDate deliveryTo,
                          UUID customerId, List<PaymentStatus> paymentStatus) {

    public OrderFilter {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        paymentStatus = paymentStatus == null ? List.of() : List.copyOf(paymentStatus);
    }

}
