package dev.peluci.doceenccoreapi.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderSimulationResquest(
        @NotNull UUID customerId,
        @NotNull List<@Valid OrderItemRequest> items) {
}
