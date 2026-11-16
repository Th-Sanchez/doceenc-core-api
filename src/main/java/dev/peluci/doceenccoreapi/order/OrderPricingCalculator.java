package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.shared.numeric.Scale;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class OrderPricingCalculator {

    public enum LineStatus {
        PRICED,
        NO_PRICE
    }

    public record Item(BigDecimal retailPrice, BigDecimal wholesalePrice, BigDecimal unitCost, int quantity) {
    }

    public record Line(LineStatus status, BigDecimal unitPrice, BigDecimal unitCost, BigDecimal subtotal) {
    }

    public record Result(List<Line> lines, BigDecimal total) {
    }

    private OrderPricingCalculator() {
    }

    public static Result calculate(PriceChannel channel, List<Item> items) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(items, "items");

        List<Line> lines = items.stream().map(item -> line(channel, item)).toList();

        BigDecimal total = lines.stream()
                .map(Line::subtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Result(lines, Scale.money(total));
    }

    private static Line line(PriceChannel channel, Item item) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(item.unitCost(), "unitCost");

        if (item.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        if (item.unitCost().signum() < 0) {
            throw new IllegalArgumentException("unitCost must not be negative");
        }

        BigDecimal unitCost = Scale.money(item.unitCost());
        BigDecimal price = switch (channel) {
            case RETAIL -> item.retailPrice();
            case WHOLESALE -> item.wholesalePrice();
        };

        if (price == null) {
            return new Line(LineStatus.NO_PRICE, null, unitCost, null);
        }

        BigDecimal unitPrice = Scale.money(price);
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }

        BigDecimal subtotal = Scale.money(unitPrice.multiply(BigDecimal.valueOf(item.quantity())));
        return new Line(LineStatus.PRICED, unitPrice, unitCost, subtotal);
    }

}
