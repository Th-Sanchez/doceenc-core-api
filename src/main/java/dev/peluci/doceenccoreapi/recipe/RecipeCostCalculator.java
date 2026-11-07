package dev.peluci.doceenccoreapi.recipe;

import dev.peluci.doceenccoreapi.shared.numeric.Scale;

import java.math.BigDecimal;
import java.util.Collection;

public final class RecipeCostCalculator {

    public record Summary(BigDecimal totalCost, BigDecimal costPerYieldUnit) {
    }

    private RecipeCostCalculator() {
    }

    public static BigDecimal itemCost(BigDecimal quantity, BigDecimal unitPrice) {
        return Scale.money(quantity.multiply(unitPrice));
    }

    public static Summary summarize(Collection<BigDecimal> itemCosts, BigDecimal yieldQuantity) {
        if (yieldQuantity.signum() <= 0) {
            throw new IllegalArgumentException("yieldQuantity must be positive");
        }

        BigDecimal total = Scale.money(itemCosts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal perUnit = Scale.unitPrice(Scale.divide(total, yieldQuantity));
        return new Summary(total, perUnit);
    }

}
