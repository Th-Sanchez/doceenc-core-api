package dev.peluci.doceenccoreapi.product.dto;

import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.UnitCost;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ProductCostResponse(
        @Schema(requiredMode = REQUIRED) BigDecimal recipeCost,
        @Schema(requiredMode = REQUIRED) BigDecimal extrasCost,
        @Schema(requiredMode = REQUIRED) BigDecimal totalCost) {

    public static ProductCostResponse from(UnitCost cost) {
        return new ProductCostResponse(cost.recipePortion(), cost.extras(), cost.total());
    }

}
