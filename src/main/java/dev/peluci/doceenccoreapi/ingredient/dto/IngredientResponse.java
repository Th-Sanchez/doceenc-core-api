package dev.peluci.doceenccoreapi.ingredient.dto;

import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.ingredient.IngredientPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IngredientResponse(
        UUID id,
        String name,
        String brandSupplier,
        CategorySummary category,
        BaseUnit baseUnit,
        BigDecimal packagePrice,
        BigDecimal packageQuantity,
        BigDecimal unitPrice,
        LocalDate lastPurchasedOn,
        BigDecimal variationPercent,
        boolean active) {

    public record CategorySummary(UUID id, String name) {
    }

    public static IngredientResponse from(Ingredient i, LocalDate lastPurchasedOn, BigDecimal variationPercent) {
        IngredientPrice p = i.getPrice();
        return new IngredientResponse(i.getId(), i.getName(), i.getBrandSupplier(),
                new CategorySummary(i.getCategory().getId(), i.getCategory().getName()),
                i.getBaseUnit(), p.getPackagePrice(), p.getPackageQuantity(), p.getUnitPrice(),
                lastPurchasedOn, variationPercent, i.isActive());
    }
}
