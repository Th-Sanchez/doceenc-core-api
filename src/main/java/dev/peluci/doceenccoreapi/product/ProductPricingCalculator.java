package dev.peluci.doceenccoreapi.product;

import dev.peluci.doceenccoreapi.shared.numeric.Scale;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

public final class ProductPricingCalculator {

    public enum MarginStatus {
        NO_PRICE,
        LOSS,
        BELOW_TARGET,
        ON_TARGET
    }

    public record UnitCost(BigDecimal recipePortion, BigDecimal extras, BigDecimal total) {
    }

    public record ChannelPricing(BigDecimal price, BigDecimal grossProfit, BigDecimal marginPercent,
                                 MarginStatus status, BigDecimal suggestedPrice) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ProductPricingCalculator() {
    }

    public static UnitCost unitCost(BigDecimal recipeTotalCost, BigDecimal recipeYieldQuantity,
                                    BigDecimal recipeQuantity, Collection<BigDecimal> extraItemCosts) {
        Objects.requireNonNull(recipeTotalCost, "recipeTotalCost");
        Objects.requireNonNull(extraItemCosts, "extraItemCosts");

        requirePositive(recipeYieldQuantity, "recipeYieldQuantity");
        requirePositive(recipeQuantity, "recipeQuantity");

        if (recipeTotalCost.signum() < 0) {
            throw new IllegalArgumentException("recipeTotalCost must not be negative");
        }

        BigDecimal recipePortion = Scale.money(Scale.divide(recipeTotalCost.multiply(recipeQuantity), recipeYieldQuantity));
        BigDecimal extras = Scale.money(extraItemCosts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        return new UnitCost(recipePortion, extras, recipePortion.add(extras));
    }

    public static ChannelPricing channel(BigDecimal unitCost, BigDecimal price, BigDecimal targetMarginPercent) {
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(targetMarginPercent, "targetMarginPercent");

        if (unitCost.signum() < 0) {
            throw new IllegalArgumentException("unitCost must not be negative");
        }

        if (targetMarginPercent.signum() < 0 || targetMarginPercent.compareTo(HUNDRED) >= 0) {
            throw new IllegalArgumentException("targetMarginPercent must be in [0,100]");
        }

        BigDecimal suggested = suggestedPrice(unitCost, targetMarginPercent);
        if (price == null) {
            return new ChannelPricing(null, null, null, MarginStatus.NO_PRICE, suggested);
        }

        BigDecimal sellingPrice = Scale.money(price);
        requirePositive(sellingPrice, "price");

        BigDecimal profit = sellingPrice.subtract(unitCost);
        BigDecimal margin = Scale.percentage(Scale.divide(profit.multiply(HUNDRED), sellingPrice));
        return new ChannelPricing(sellingPrice, Scale.money(profit), margin,
                status(profit, sellingPrice, targetMarginPercent), suggested);
    }

    private static BigDecimal suggestedPrice(BigDecimal unitCost, BigDecimal targetMarginPercent) {
        if (unitCost.signum() == 0) {
            return null;
        }
        return Scale.divideMoneyUp(unitCost.multiply(HUNDRED), HUNDRED.subtract(targetMarginPercent));
    }

    private static MarginStatus status(BigDecimal profit, BigDecimal price, BigDecimal targetMarginPercent) {
        if (profit.signum() < 0) {
            return MarginStatus.LOSS;
        }

        boolean reachesTarget = profit.multiply(HUNDRED).compareTo(targetMarginPercent.multiply(price)) >= 0;
        return reachesTarget ? MarginStatus.ON_TARGET : MarginStatus.BELOW_TARGET;
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

}
