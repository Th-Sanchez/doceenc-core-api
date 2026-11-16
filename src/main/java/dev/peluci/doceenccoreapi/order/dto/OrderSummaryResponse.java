package dev.peluci.doceenccoreapi.order.dto;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.order.FulfillmentType;
import dev.peluci.doceenccoreapi.order.Order;
import dev.peluci.doceenccoreapi.order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record OrderSummaryResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) Long orderNumber,
        @Schema(requiredMode = REQUIRED) OrderStatus status,
        @Schema(requiredMode = REQUIRED) CustomerSummary customer,
        @Schema(requiredMode = REQUIRED) FulfillmentType fulfillmentType,
        @Schema(requiredMode = REQUIRED, nullable = true) LocalDate deliveryDate,
        @Schema(requiredMode = REQUIRED) int itemCount,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal totalAmount) {

    public record CustomerSummary(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED, nullable = true) String storeName) {

        static CustomerSummary from(Customer customer) {
            return new CustomerSummary(customer.getId(), customer.getName(), customer.getStoreName());
        }

    }

    public static OrderSummaryResponse from(Order order, BigDecimal totalAmount) {
        return new OrderSummaryResponse(order.getId(),  order.getOrderNumber(), order.getStatus(),
                CustomerSummary.from(order.getCustomer()), order.getFulfillmentType(), order.getDeliveryDate(),
                order.getItems().size(), totalAmount);
    }

}
