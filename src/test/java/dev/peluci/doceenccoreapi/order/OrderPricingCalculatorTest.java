package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Item;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Line;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.LineStatus;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Result;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrderPricingCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static Item item(String retail, String wholesale, String cost, int quantity) {
        return new Item(retail == null ? null : bd(retail), wholesale == null ? null : bd(wholesale),
                bd(cost), quantity);
    }

    @Nested
    class ChannelTest {

        private final Item brigadeiro = item("15.00", "11.54", "7.50", 3);

        @Test
        void retailUsesTheRetailPrice() {
            Result result = OrderPricingCalculator.calculate(PriceChannel.RETAIL, List.of(brigadeiro));

            Line line = result.lines().get(0);
            assertThat(line.status()).isEqualTo(LineStatus.PRICED);
            assertThat(line.unitPrice()).isEqualTo(bd("15.00"));
            assertThat(line.unitCost()).isEqualTo(bd("7.50"));
            assertThat(line.subtotal()).isEqualTo(bd("45.00"));
            assertThat(result.total()).isEqualTo(bd("45.00"));
        }

        @Test
        void wholesaleUsesTheWholesalePrice() {
            Result result = OrderPricingCalculator.calculate(PriceChannel.WHOLESALE, List.of(brigadeiro));

            Line line = result.lines().get(0);
            assertThat(line.status()).isEqualTo(LineStatus.PRICED);
            assertThat(line.unitPrice()).isEqualTo(bd("11.54"));
            assertThat(line.subtotal()).isEqualTo(bd("34.62"));
            assertThat(result.total()).isEqualTo(bd("34.62"));
        }

        @Test
        void aMissingPriceInTheChannelIsFlaggedNotThrown() {
            Item retailOnly = item("15.00", null, "7.50", 2);

            Line wholesale = OrderPricingCalculator.calculate(PriceChannel.WHOLESALE, List.of(retailOnly))
                    .lines().get(0);
            assertThat(wholesale.status()).isEqualTo(LineStatus.NO_PRICE);
            assertThat(wholesale.unitPrice()).isNull();
            assertThat(wholesale.subtotal()).isNull();
            assertThat(wholesale.unitCost()).isEqualTo(bd("7.50"));

            Line retail = OrderPricingCalculator.calculate(PriceChannel.RETAIL, List.of(retailOnly))
                    .lines().get(0);
            assertThat(retail.status()).isEqualTo(LineStatus.PRICED);
        }

    }

    @Nested
    class TotalTest {

        private final List<Item> items = List.of(
                item("15.00", "11.54", "7.50", 3),
                item("160.00", "120.00", "72.00", 1),
                item("8.50", null, "3.00", 10));

        @Test
        void sumsTheSubtotalsOfTheLines() {
            Result result = OrderPricingCalculator.calculate(PriceChannel.RETAIL, items);

            assertThat(result.lines()).extracting(Line::subtotal)
                    .containsExactly(bd("45.00"), bd("160.00"), bd("85.00"));
            assertThat(result.total()).isEqualTo(bd("290.00"));
        }

        @Test
        void leavesUnpricedLinesOutOfTheTotalAndKeepsTheInputOrder() {
            Result result = OrderPricingCalculator.calculate(PriceChannel.WHOLESALE, items);

            assertThat(result.lines()).extracting(Line::status)
                    .containsExactly(LineStatus.PRICED, LineStatus.PRICED, LineStatus.NO_PRICE);
            assertThat(result.lines()).extracting(Line::unitCost)
                    .containsExactly(bd("7.50"), bd("72.00"), bd("3.00"));
            assertThat(result.total()).isEqualTo(bd("154.62"));
        }

        @Test
        void anEmptyOrderTotalsZeroAtTheMonetaryScale() {
            Result result = OrderPricingCalculator.calculate(PriceChannel.RETAIL, List.of());

            assertThat(result.lines()).isEmpty();
            assertThat(result.total()).isEqualTo(bd("0.00"));
        }

    }

    @Nested
    class RoundingTest {

        @Test
        void roundsTheUnitPriceBeforeMultiplyingSoTheLineAddsUp() {
            Result result = OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(item("10.005", null, "1.00", 3)));

            Line line = result.lines().get(0);
            assertThat(line.unitPrice()).isEqualTo(bd("10.01"));
            assertThat(line.subtotal()).isEqualTo(bd("30.03"));
            assertThat(result.total()).isEqualTo(bd("30.03"));
        }

        @Test
        void roundsTheUnitCostHalfUpToCents() {
            Line line = OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(item("10.00", null, "3.335", 1))).lines().get(0);

            assertThat(line.unitCost()).isEqualTo(bd("3.34"));
        }

        @Test
        void carriesTheUnitCostWithoutMultiplyingItByTheQuantity() {
            Line line = OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(item("15.00", null, "7.50", 4))).lines().get(0);

            assertThat(line.unitCost()).isEqualTo(bd("7.50"));
            assertThat(line.subtotal()).isEqualTo(bd("60.00"));
        }

    }

    @Nested
    class ValidationTest {

        @Test
        void rejectsNonPositiveQuantities() {
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(item("15.00", null, "7.50", 0))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(item("15.00", null, "7.50", -1))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNonPositivePrices() {
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(item("0.00", null, "7.50", 1))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(PriceChannel.WHOLESALE,
                    List.of(item(null, "-1.00", "7.50", 1))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsANegativeCost() {
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(item("15.00", null, "-0.01", 1))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsMissingArguments() {
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(null, List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(PriceChannel.RETAIL, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> OrderPricingCalculator.calculate(PriceChannel.RETAIL,
                    List.of(new Item(bd("15.00"), null, null, 1))))
                    .isInstanceOf(NullPointerException.class);
        }

    }

}