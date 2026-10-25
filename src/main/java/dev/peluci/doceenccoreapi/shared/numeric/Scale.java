package dev.peluci.doceenccoreapi.shared.numeric;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for BigDecimal scales and rounding. Never use double/float for
 * money or quantities.
 *
 * <p>Rule: carry {@link #INTERMEDIATE} digits through the calculation and round only when a
 * value is stored or shown. Rounding at every step accumulates error (for example, a price per
 * gram rounded to 4 digits, then multiplied by hundreds of grams).
 *
 * <p>Database columns must match: money NUMERIC(12,2), unit price NUMERIC(14,4),
 * quantity NUMERIC(14,4), percentage NUMERIC(7,2). The numbers below are a proposal:
 * keep them aligned with the Milestone 1 document.
 */
public final class Scale {

    /** Monetary amounts (BRL): cents. */
    public static final int MONEY = 2;

    /** Price per base unit (per gram, per ml, per unit): small values need more digits. */
    public static final int UNIT_PRICE = 4;

    /** Quantities of ingredients in recipes. */
    public static final int QUANTITY = 4;

    /** Percentages such as margins (12.50 means 12.50%). */
    public static final int PERCENTAGE = 2;

    /** Precision kept in intermediate divisions. */
    public static final int INTERMEDIATE = 10;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Scale() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY, ROUNDING);
    }

    public static BigDecimal unitPrice(BigDecimal value) {
        return value.setScale(UNIT_PRICE, ROUNDING);
    }

    public static BigDecimal quantity(BigDecimal value) {
        return value.setScale(QUANTITY, ROUNDING);
    }

    public static BigDecimal percentage(BigDecimal value) {
        return value.setScale(PERCENTAGE, ROUNDING);
    }

    /** Division with intermediate precision. Never use BigDecimal.divide without a scale (it can throw). */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, INTERMEDIATE, ROUNDING);
    }

}
