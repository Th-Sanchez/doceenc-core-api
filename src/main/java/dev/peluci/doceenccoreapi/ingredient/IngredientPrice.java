package dev.peluci.doceenccoreapi.ingredient;

import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
public class IngredientPrice {

    @Column(name = "package_price", nullable = false, precision = 12, scale = Scale.MONEY)
    private BigDecimal packagePrice;

    @Column(name = "package_quantity", nullable = false, precision = 14, scale = Scale.QUANTITY)
    private BigDecimal packageQuantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = Scale.UNIT_PRICE)
    private BigDecimal unitPrice;

    protected IngredientPrice() {
    }

    public IngredientPrice(BigDecimal packagePrice, BigDecimal packageQuantity, BigDecimal unitPrice) {
        this.packagePrice = packagePrice;
        this.packageQuantity = packageQuantity;
        this.unitPrice = unitPrice;
    }

    public static IngredientPrice of(BigDecimal packagePrice, BigDecimal quantity,
                                     InputUnit inputUnit, BaseUnit ingredientUnit) {
        Objects.requireNonNull(packagePrice, "packagePrice");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(inputUnit, "inputUnit");
        Objects.requireNonNull(ingredientUnit, "ingredientUnit");

        if (inputUnit.baseUnit() != ingredientUnit) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INCOMPATIBLE_UNIT,
                    "Unit" + inputUnit + " is not compatible with an ingredient measured in " + ingredientUnit + ".");
        }
        if (packagePrice.signum() <= 0) {
            throw new IllegalArgumentException("packagePrice must be positive");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        BigDecimal price = Scale.money(packagePrice);
        BigDecimal baseQuantity = inputUnit.toBase(quantity);
        if (baseQuantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity is too small");
        }
        BigDecimal unitPrice = Scale.unitPrice(Scale.divide(price, baseQuantity));
        return new IngredientPrice(price, baseQuantity, unitPrice);
    }

    public BigDecimal getPackagePrice() {
        return packagePrice;
    }

    public BigDecimal getPackageQuantity() {
        return packageQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        IngredientPrice that = (IngredientPrice) o;
        return Objects.equals(packagePrice, that.packagePrice) && Objects.equals(packageQuantity, that.packageQuantity) && Objects.equals(unitPrice, that.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packagePrice, packageQuantity, unitPrice);
    }
}
