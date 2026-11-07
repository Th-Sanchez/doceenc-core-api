package dev.peluci.doceenccoreapi.recipe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RecipeCostCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void itemCostsIsExactProductRoundedToCents() {
        assertThat(RecipeCostCalculator.itemCost(bd("250.0000"), bd("0.012000")))
                .isEqualTo(bd("3.00"));
    }

    @Test
    void itemCostRoundHalfUp() {
        assertThat(RecipeCostCalculator.itemCost(bd("1.0000"), bd("0.005000")))
                .isEqualTo(bd("0.01"));
    }

    @Test
    void tinyItemCostBecomesZeroCents() {
        assertThat(RecipeCostCalculator.itemCost(bd("1.0000"), bd("0.004000")))
                .isEqualTo(bd("0.00"));
    }

    @Test
    void totalIsTheSumOfAlreadyRoundedItemCosts() {
        List<BigDecimal> items = List.of(
                RecipeCostCalculator.itemCost(bd("1.0000"), bd("0.005000")),
                RecipeCostCalculator.itemCost(bd("1.0000"), bd("0.005000")),
                RecipeCostCalculator.itemCost(bd("1.0000"), bd("0.005000"))
        );

        RecipeCostCalculator.Summary summary = RecipeCostCalculator.summarize(items, bd("1.0000"));

        assertThat(summary.totalCost()).isEqualTo(bd("0.03"));
    }

    @Test
    void costPerYieldUnitKeepsUnitPricePrecision() {
        RecipeCostCalculator.Summary summary = RecipeCostCalculator.summarize(List.of(bd("10.00"), bd("7.50")), bd("40.0000"));

        assertThat(summary.totalCost()).isEqualTo(bd("17.50"));
        assertThat(summary.costPerYieldUnit()).isEqualTo(bd("0.437500"));
    }

    @Test
    void costPerYieldUnitRoundsHalfUpAtSixDecimals() {
        RecipeCostCalculator.Summary summary = RecipeCostCalculator.summarize(List.of(bd("10.00")), bd("3.0000"));
        assertThat(summary.costPerYieldUnit()).isEqualTo(bd("3.333333"));
    }

    @Test
    void emptyItemListGivesZeroWithCorrectSales() {
        RecipeCostCalculator.Summary summary = RecipeCostCalculator.summarize(List.of(), bd("10.0000"));

        assertThat(summary.totalCost()).isEqualTo(bd("0.00"));
        assertThat(summary.costPerYieldUnit()).isEqualTo(bd("0.000000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0000", "-1", "-0.0001"})
    void nonPositiveYieldIsRejected(String yield) {
        assertThatThrownBy(() -> RecipeCostCalculator.summarize(List.of(bd("1.00")), bd(yield)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
