package dev.peluci.doceenccoreapi.order.dto;

import dev.peluci.doceenccoreapi.order.FulfillmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderRequest(
        @NotNull UUID customerId,
        @NotNull FulfillmentType fulfillmentType,
        @Size(max = 255) String deliveryAddress,
        LocalDate deliveryDate,
        @Size(max = 1000) String notes,
        @NotNull List<@Valid OrderItemRequest> items) {
}
