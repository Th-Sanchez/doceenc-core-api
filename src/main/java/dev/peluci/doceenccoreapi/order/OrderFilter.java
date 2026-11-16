package dev.peluci.doceenccoreapi.order;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderFilter(List<OrderStatus> statuses, LocalDate deliveryFrom, LocalDate deliveryTo, UUID customerId) {

    public OrderFilter {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }

}
