package dev.peluci.doceenccoreapi.order.dto;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.customer.CustomerType;
import dev.peluci.doceenccoreapi.order.FulfillmentType;
import dev.peluci.doceenccoreapi.order.LineIssue;
import dev.peluci.doceenccoreapi.order.Order;
import dev.peluci.doceenccoreapi.order.OrderStatus;
import dev.peluci.doceenccoreapi.order.PriceChannel;
import dev.peluci.doceenccoreapi.order.PricedOrder;
import dev.peluci.doceenccoreapi.order.PricedOrder.PricedLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record OrderDetailResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) Long orderNumber,
        @Schema(requiredMode = REQUIRED) OrderStatus status,
        @Schema(requiredMode = REQUIRED) CustomerSummary customer,
        @Schema(requiredMode = REQUIRED) FulfillmentType fulfillmentType,
        @Schema(requiredMode = REQUIRED, nullable = true) PriceChannel priceChannel,
        @Schema(requiredMode = REQUIRED, nullable = true) String deliveryAddress,
        @Schema(requiredMode = REQUIRED, nullable = true) LocalDate deliveryDate,
        @Schema(requiredMode = REQUIRED, nullable = true) String notes,
        @Schema(requiredMode = REQUIRED) List<ItemResponse> items,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal totalAmount,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant confirmedAt,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant deliveredAt,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant cancelledAt,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {

    public record CustomerSummary(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) CustomerType type,
            @Schema(requiredMode = REQUIRED, nullable = true) String storeName) {

        static CustomerSummary from(Customer customer) {
            return new CustomerSummary(customer.getId(), customer.getName(), customer.getType(), customer.getStoreName());
        }

    }

    public record ItemResponse(
            @Schema(requiredMode = REQUIRED) UUID productId,
            @Schema(requiredMode = REQUIRED) String productName,
            @Schema(requiredMode = REQUIRED) int quantity,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal unitPrice,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal unitCost,
            @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal subtotal,
            @Schema(requiredMode = REQUIRED, nullable = true) LineIssue issue) {

        static ItemResponse from(PricedLine line) {
            return new ItemResponse(line.item().getProduct().getId(), line.productName(),
                    line.item().getQuantity(), line.unitPrice(), line.unitCost(), line.subtotal(), line.issue());
        }

    }

    public static OrderDetailResponse from(PricedOrder priced) {
        Order order = priced.order();

        List<ItemResponse> items = priced.lines().stream()
                .map(ItemResponse::from)
                .sorted(Comparator.comparing(ItemResponse::productName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new OrderDetailResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                CustomerSummary.from(order.getCustomer()), order.getFulfillmentType(), priced.channel(),
                order.getDeliveryAddress(), order.getDeliveryDate(), order.getNotes(), items, priced.total(),
                order.getConfirmedAt(), order.getDeliveredAt(), order.getCancelledAt(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
