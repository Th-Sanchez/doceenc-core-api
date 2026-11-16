package dev.peluci.doceenccoreapi.order.dto;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.order.FulfillmentType;
import dev.peluci.doceenccoreapi.order.Order;
import dev.peluci.doceenccoreapi.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        Long orderNumber,
        OrderStatus status,
        CustomerSummary customer,
        FulfillmentType fulfillmentType,
        LocalDate deliveryDate,
        int itemCount,
        BigDecimal totalAmount) {

    public record CustomerSummary(UUID id, String name, String storeName) {

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
