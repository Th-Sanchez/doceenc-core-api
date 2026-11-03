package dev.peluci.doceenccoreapi.ingredient.dto;

import dev.peluci.doceenccoreapi.ingredient.IngredientPrice;
import dev.peluci.doceenccoreapi.ingredient.IngredientPriceHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceHistoryResponse(
        UUID id,
        BigDecimal packagePrice,
        BigDecimal packageQuantity,
        BigDecimal unitPrice,
        LocalDate purchasedOn,
        Instant createdAt) {

    public static PriceHistoryResponse from(IngredientPriceHistory h) {
        IngredientPrice p = h.getPrice();
        return new PriceHistoryResponse(h.getId(), p.getPackagePrice(), p.getPackageQuantity(),
                p.getUnitPrice(), h.getPurchasedOn(), h.getCreatedAt());
    }

}
