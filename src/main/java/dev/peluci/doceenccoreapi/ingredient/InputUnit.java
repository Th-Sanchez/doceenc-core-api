package dev.peluci.doceenccoreapi.ingredient;

import dev.peluci.doceenccoreapi.shared.numeric.Scale;

import java.math.BigDecimal;

public enum InputUnit {

    G(BaseUnit.GRAM, "1"),
    KG(BaseUnit.GRAM, "1000"),
    ML(BaseUnit.MILLILITER, "1"),
    L(BaseUnit.MILLILITER, "1000"),
    UNIT(BaseUnit.UNIT, "1");

    private final BaseUnit baseUnit;
    private final BigDecimal factor;

    InputUnit(BaseUnit baseUnit, String factor) {
        this.baseUnit = baseUnit;
        this.factor = new BigDecimal(factor);
    }

    public BaseUnit baseUnit() {
        return baseUnit;
    }

    public BigDecimal toBase(BigDecimal quantity) {
        return Scale.quantity(quantity.multiply(factor));
    }
}
