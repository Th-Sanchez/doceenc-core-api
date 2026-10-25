package dev.peluci.doceenccoreapi.shared.numeric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class ScaleTest {

    @Test
    void moneyRoundsHalfUpToCents() {
        assertThat(Scale.money(new BigDecimal("2.665"))).isEqualByComparingTo("2.67");
        assertThat(Scale.money(new BigDecimal("2.665")).scale()).isEqualTo(Scale.MONEY);
    }

    @Test
    void unitPriceKeepsFourDigits() {
        BigDecimal value = Scale.unitPrice(new BigDecimal("0.02345"));

        assertThat(value).isEqualByComparingTo("0.0235");
        assertThat(value.scale()).isEqualTo(Scale.UNIT_PRICE);
    }

    @Test
    void quantityAndPercentageUseTheirOwnScales() {
        assertThat(Scale.quantity(new BigDecimal("1.5")).scale()).isEqualTo(Scale.QUANTITY);
        assertThat(Scale.percentage(new BigDecimal("12.345")).scale()).isEqualTo(Scale.PERCENTAGE);
        assertThat(Scale.percentage(new BigDecimal("12.345"))).isEqualByComparingTo("12.35");
    }

    @Test
    void divideKeepsIntermediatePercentageAndNeverThrowsOnNonTerminatingResults() {
        BigDecimal third = Scale.divide(BigDecimal.ONE, new BigDecimal("3"));

        assertThat(third).isEqualByComparingTo("0.3333333333");
        assertThat(third.scale()).isEqualTo(Scale.INTERMEDIATE);
    }

    @Test
    void roundingOnlyAtTheEndAvoidsAccumulatedError() {
        BigDecimal unitPrice = Scale.divide(new BigDecimal("10.00"), new BigDecimal("3"));

        BigDecimal cost = Scale.money(unitPrice.multiply(new BigDecimal("7")));

        assertThat(cost).isEqualByComparingTo("23.33");
    }

}
