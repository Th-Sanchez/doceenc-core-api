package dev.peluci.doceenccoreapi.product;

import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.ChannelPricing;
import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.UnitCost;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static dev.peluci.doceenccoreapi.product.ProductPricingCalculator.MarginStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProductPricingCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    class UnitCostTest {

        @Test
        void sumsRecipePortionAndPackaging() {
            UnitCost cost = ProductPricingCalculator.unitCost(bd("68.00"), bd("10"), bd("1"), List.of(bd("0.70")));

            assertThat(cost.recipePortion()).isEqualTo(bd("6.80"));
            assertThat(cost.extras()).isEqualTo(bd("0.70"));
            assertThat(cost.total()).isEqualTo(bd("7.50"));
        }

        @Test
        void scalesTheRecipePortionByTheQuantityUsed() {
            UnitCost cost = ProductPricingCalculator.unitCost(bd("360.00"), bd("500"), bd("100"), List.of());

            assertThat(cost.recipePortion()).isEqualTo(bd("72.00"));
            assertThat(cost.extras()).isEqualTo(bd("0.00"));
            assertThat(cost.total()).isEqualTo(bd("72.00"));
        }

        @Test
        void sixBrigadeirosInABox() {
            UnitCost cost = ProductPricingCalculator.unitCost(bd("72.00"), bd("100"), bd("6"), List.of(bd("2.50")));

            assertThat(cost.recipePortion()).isEqualTo(bd("4.32"));
            assertThat(cost.total()).isEqualTo(bd("6.82"));
        }

        @Test
        void usesGramsWhenTheRecipeYieldIsInGrams() {
            UnitCost cost = ProductPricingCalculator.unitCost(bd("64.00"), bd("1000"), bd("20"), List.of());

            assertThat(cost.total()).isEqualTo(bd("1.28"));
        }

        @Test
        void roundsOnlyOnceHalfUp() {
            assertThat(ProductPricingCalculator.unitCost(bd("10.00"), bd("3"), bd("1"), List.of()).total())
                    .isEqualTo(bd("3.33"));
            assertThat(ProductPricingCalculator.unitCost(bd("20.00"), bd("3"), bd("1"), List.of()).total())
                    .isEqualTo(bd("6.67"));
        }

        @Test
        void rejectsNonPositiveQuantities() {
            assertThatThrownBy(() -> ProductPricingCalculator.unitCost(bd("10.00"), bd("0"), bd("1"), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProductPricingCalculator.unitCost(bd("10.00"), bd("1"), bd("0"), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

    }

    @Nested
    class ChannelTest {

        private final BigDecimal cost = bd("7.50");

        @Test
        void retailOnTarget() {
            ChannelPricing pricing = ProductPricingCalculator.channel(cost, bd("15.00"), bd("50.00"));

            assertThat(pricing.price()).isEqualTo(bd("15.00"));
            assertThat(pricing.grossProfit()).isEqualTo(bd("7.50"));
            assertThat(pricing.marginPercent()).isEqualTo(bd("50.00"));
            assertThat(pricing.status()).isEqualTo(MarginStatus.ON_TARGET);
            assertThat(pricing.suggestedPrice()).isEqualTo(bd("15.00"));
        }

        @Test
        void retailBelowTarget() {
            ChannelPricing pricing = ProductPricingCalculator.channel(cost, bd("11.00"), bd("50.00"));

            assertThat(pricing.grossProfit()).isEqualTo(bd("3.50"));
            assertThat(pricing.marginPercent()).isEqualTo(bd("31.82"));
            assertThat(pricing.status()).isEqualTo(MarginStatus.BELOW_TARGET);
            assertThat(pricing.suggestedPrice()).isEqualTo(bd("15.00"));
        }

        @Test
        void wholesaleJustBelowTarget() {
            ChannelPricing pricing = ProductPricingCalculator.channel(cost, bd("11.50"), bd("35.00"));

            assertThat(pricing.grossProfit()).isEqualTo(bd("4.00"));
            assertThat(pricing.marginPercent()).isEqualTo(bd("34.78"));
            assertThat(pricing.status()).isEqualTo(MarginStatus.BELOW_TARGET);
            assertThat(pricing.suggestedPrice()).isEqualTo(bd("11.54"));
        }

        @Test
        void suggestedPriceIsRoundedUp() {
            assertThat(ProductPricingCalculator.channel(bd("7.50"), null, bd("35.00")).suggestedPrice())
                    .isEqualTo(bd("11.54"));
            assertThat(ProductPricingCalculator.channel(bd("10.00"), null, bd("40.00")).suggestedPrice())
                    .isEqualTo(bd("16.67"));
            assertThat(ProductPricingCalculator.channel(bd("10.01"), null, bd("30.00")).suggestedPrice())
                    .isEqualTo(bd("14.30"));
            assertThat(ProductPricingCalculator.channel(bd("10.01"), null, bd("40.00")).suggestedPrice())
                    .isEqualTo(bd("16.69"));
        }

        @Test
        void priceBelowCostIsALoss() {
            ChannelPricing pricing = ProductPricingCalculator.channel(cost, bd("7.00"), bd("50.00"));

            assertThat(pricing.grossProfit()).isEqualTo(bd("-0.50"));
            assertThat(pricing.marginPercent()).isEqualTo(bd("-7.14"));
            assertThat(pricing.status()).isEqualTo(MarginStatus.LOSS);
        }

        @Test
        void priceEqualToCostIsBelowTargetNotALoss() {
            ChannelPricing pricing = ProductPricingCalculator.channel(cost, bd("7.50"), bd("50.00"));

            assertThat(pricing.marginPercent()).isEqualTo(bd("0.00"));
            assertThat(pricing.status()).isEqualTo(MarginStatus.BELOW_TARGET);
        }

        @Test
        void boundaryIsInclusive() {
            assertThat(ProductPricingCalculator.channel(cost, bd("12.50"), bd("40.00")).status())
                    .isEqualTo(MarginStatus.ON_TARGET);
            assertThat(ProductPricingCalculator.channel(cost, bd("12.49"), bd("40.00")).status())
                    .isEqualTo(MarginStatus.BELOW_TARGET);
        }

        @Test
        void statusUsesTheExactMarginNotTheRoundedOne() {
            ChannelPricing pricing = ProductPricingCalculator.channel(bd("500.00"), bd("999.99"), bd("50.00"));

            assertThat(pricing.marginPercent()).isEqualTo(bd("50.00"));
            assertThat(pricing.status()).isEqualTo(MarginStatus.BELOW_TARGET);
        }

        @Test
        void noPriceStillSuggestsOne() {
            ChannelPricing pricing = ProductPricingCalculator.channel(cost, null, bd("50.00"));

            assertThat(pricing.price()).isNull();
            assertThat(pricing.grossProfit()).isNull();
            assertThat(pricing.marginPercent()).isNull();
            assertThat(pricing.status()).isEqualTo(MarginStatus.NO_PRICE);
            assertThat(pricing.suggestedPrice()).isEqualTo(bd("15.00"));
        }

        @Test
        void zeroCostHasNoSuggestionAndFullMargin() {
            assertThat(ProductPricingCalculator.channel(bd("0.00"), null, bd("50.00")).suggestedPrice()).isNull();

            ChannelPricing pricing = ProductPricingCalculator.channel(bd("0.00"), bd("10.00"), bd("50.00"));
            assertThat(pricing.marginPercent()).isEqualTo(bd("100.00"));
            assertThat(pricing.status()).isEqualTo(MarginStatus.ON_TARGET);
            assertThat(pricing.suggestedPrice()).isNull();
        }

        @Test
        void zeroTargetSuggestsTheCost() {
            assertThat(ProductPricingCalculator.channel(cost, null, bd("0.00")).suggestedPrice())
                    .isEqualTo(bd("7.50"));
        }

        @Test
        void priceIsNormalizedToCents() {
            assertThat(ProductPricingCalculator.channel(cost, bd("15"), bd("50")).price()).isEqualTo(bd("15.00"));
        }

        @Test
        void rejectsInvalidInput() {
            assertThatThrownBy(() -> ProductPricingCalculator.channel(cost, bd("0.00"), bd("50.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProductPricingCalculator.channel(cost, bd("-1.00"), bd("50.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProductPricingCalculator.channel(cost, bd("10.00"), bd("100.00")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProductPricingCalculator.channel(cost, bd("10.00"), bd("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProductPricingCalculator.channel(bd("-1.00"), bd("10.00"), bd("50.00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

    }

    @Nested
    class SuggestedPriceGuarantee {

        @Test
        void suggestedPriceIsTheSmallestPriceThatReachesTheTarget() {
            List<String> costs = List.of("0.01", "0.99", "7.50", "10.01", "33.33", "64.01", "123.45", "999.99");
            List<String> targets = List.of("0.00", "12.50", "33.33", "35.00", "40.00", "50.00", "66.67", "99.99");

            for (String c : costs) {
                for (String t : targets) {
                    BigDecimal suggested = ProductPricingCalculator.channel(bd(c), null, bd(t)).suggestedPrice();

                    assertThat(ProductPricingCalculator.channel(bd(c), suggested, bd(t)).status())
                            .as("cost %s, taget %s, suggested %s", c, t, suggested)
                            .isEqualTo(MarginStatus.ON_TARGET);

                    BigDecimal oneCentLess = suggested.subtract(bd("0.01"));
                    if (oneCentLess.signum() > 0) {
                        assertThat(ProductPricingCalculator.channel(bd(c), oneCentLess, bd(t)).status())
                                .as("cost %s, taget %s, one cent below %s", c, t, suggested)
                                .isNotEqualTo(MarginStatus.ON_TARGET);
                    }
                }
            }
        }
    }

}
