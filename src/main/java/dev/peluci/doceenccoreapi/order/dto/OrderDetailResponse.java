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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID id,
        Long orderNumber,
        OrderStatus status,
        CustomerSummary customer,
        FulfillmentType fulfillmentType,
        PriceChannel priceChannel,
        String deliveryAddress,
        LocalDate deliveryDate,
        String notes,
        List<ItemResponse> items,
        BigDecimal totalAmount,
        Instant confirmedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt) {

    public record CustomerSummary(UUID id, String name, CustomerType type, String storeName) {

        static CustomerSummary from(Customer customer) {
            return new CustomerSummary(customer.getId(), customer.getName(), customer.getType(), customer.getStoreName());
        }

    }

    public record ItemResponse(UUID productId, String productName, int quantity, BigDecimal unitPrice,
                               BigDecimal unitCost, BigDecimal subtotal, LineIssue issue) {

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
