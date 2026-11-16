package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Item;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Line;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Result;
import dev.peluci.doceenccoreapi.product.Product;

import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public record PricedOrder(Order order, PriceChannel channel, List<PricedLine> lines,
                          BigDecimal total, Result calculation) {

    public record PricedLine(OrderItem item, String productName, BigDecimal unitPrice, BigDecimal unitCost,
                             BigDecimal subtotal, LineIssue issue) {
    }

    public static PricedOrder of(Order order) {
        return order.isPending() ? live(order) : frozen(order);
    }

    private static PricedOrder live(Order order) {
        PriceChannel channel = channelOf(order.getCustomer());

        List<OrderItem> priceable = order.getItems().stream()
                .filter(item -> item.getProduct().isActive())
                .toList();
        Result result = OrderPricingCalculator.calculate(channel,
                priceable.stream().map(PricedOrder::calculatorItem).toList());

        Map<OrderItem, Line> lineByItem = new IdentityHashMap<>();
        for (int i = 0; i < priceable.size(); i++) {
            lineByItem.put(priceable.get(i), result.lines().get(i));
        }

        List<PricedLine> lines = order.getItems().stream().map(item -> {
            Product product = item.getProduct();
            Line line = lineByItem.get(item);

            if (line == null) {
                return new PricedLine(item, product.getName(), null, null, null, LineIssue.ARCHIVED);
            }

            if (line.status() == OrderPricingCalculator.LineStatus.NO_PRICE) {
                return new PricedLine(item, product.getName(), null, line.unitCost(), null, LineIssue.NO_PRICE);
            }

            return new PricedLine(item, product.getName(), line.unitPrice(), line.unitCost(), line.subtotal(), null);
        }).toList();

        return new PricedOrder(order, order.getPriceChannel(), lines, order.getTotalAmount(), null);
    }

    private static PricedOrder frozen(Order order) {
        List<PricedLine> lines = order.getItems().stream()
                .map(item -> new PricedLine(item, nameOf(item), item.getUnitPrice(), item.getUnitCost(),
                        item.frozenSubtotal(), null))
                .toList();

        return new PricedOrder(order, order.getPriceChannel(), lines, order.getTotalAmount(), null);
    }

    private static String nameOf(OrderItem item) {
        return item.getProductName() != null ? item.getProductName() : item.getProduct().getName();
    }

    private static Item calculatorItem(OrderItem item) {
        Product product = item.getProduct();
        return new Item(product.getRetailPrice(), product.getWholesalePrice(),
                product.unitCost().total(), item.getQuantity());
    }

    private static PriceChannel channelOf(Customer customer) {
        return switch (customer.getType()) {
            case RETAIL -> PriceChannel.RETAIL;
            case WHOLESALE -> PriceChannel.WHOLESALE;
        };
    }
}
