package dev.peluci.doceenccoreapi.ingredient;

import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IngredientPriceTest {

    @ParameterizedTest(name = "R$ {0} por {1} {2} -> {4} por unidade-base, embalagem de {5}")
    @CsvSource({
            "4.99,  1,    KG,   GRAM,       0.004990, 1000.0000",
            "8.90,  1000, G,    GRAM,       0.008900, 1000.0000",
            "7.50,  1,    L,    MILLILITER, 0.007500, 1000.0000",
            "5.00,  900,  ML,   MILLILITER, 0.005556, 900.0000",
            "18.00, 12,   UNIT, UNIT,       1.500000, 12.0000",
            "0.01,  5,    KG,   GRAM,       0.000002, 5000.0000"
    })
    void derivesTheUnitPriceInTheBaseUnit(BigDecimal price, BigDecimal quantity, InputUnit unit, BaseUnit base,
                                          BigDecimal expectedUnitPrice, BigDecimal expectedPackageQuantity) {
        IngredientPrice result = IngredientPrice.of(price, quantity, unit, base);

        assertThat(result.getUnitPrice()).isEqualTo(expectedUnitPrice);
        assertThat(result.getPackageQuantity()).isEqualTo(expectedPackageQuantity);
        assertThat(result.getPackagePrice()).isEqualTo(price);
    }

    @Test
    void roundsThePackagePriceToCents() {
        IngredientPrice result = IngredientPrice.of(
                new BigDecimal("8.994"), BigDecimal.ONE, InputUnit.KG, BaseUnit.GRAM);
        assertThat(result.getPackagePrice()).isEqualTo(new BigDecimal("8.99"));
    }

    @ParameterizedTest
    @CsvSource({
            "KG, MILLILITER",
            "L,  GRAM",
            "G,  UNIT",
            "UNIT, GRAM"
    })
    void rejectsAnInputUnitThatDoesNotMatchTheIngredientUnit(InputUnit unit, BaseUnit base) {
        assertThatThrownBy(() -> IngredientPrice.of(new BigDecimal("8.90"), BigDecimal.ONE, unit, base))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INCOMPATIBLE_UNIT);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void rejectsNonPositivePrice(String price) {
        assertThatThrownBy(() -> IngredientPrice.of(new BigDecimal(price), BigDecimal.ONE, InputUnit.KG, BaseUnit.GRAM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.001"})
    void rejectsNonPositiveQuantity(String quantity) {
        assertThatThrownBy(() -> IngredientPrice.of(new BigDecimal("8.90"), new BigDecimal(quantity), InputUnit.KG, BaseUnit.GRAM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPriceThatRoundsToZeroPerUnit() {
        assertThatThrownBy(() -> IngredientPrice.of(new BigDecimal("0.01"), new BigDecimal("999999"), InputUnit.KG, BaseUnit.GRAM))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PRICE_OUT_OF_RANGE));
    }

}
