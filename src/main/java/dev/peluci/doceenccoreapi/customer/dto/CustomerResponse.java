package dev.peluci.doceenccoreapi.customer.dto;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.customer.CustomerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record CustomerResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) CustomerType type,
        @Schema(requiredMode = REQUIRED, nullable = true) String phone,
        @Schema(requiredMode = REQUIRED, nullable = true) String address,
        @Schema(requiredMode = REQUIRED, nullable = true) String storeName,
        @Schema(requiredMode = REQUIRED, nullable = true) String notes,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getType(), c.getPhone(), c.getAddress(),
                c.getStoreName(), c.getNotes(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt());
    }

}
