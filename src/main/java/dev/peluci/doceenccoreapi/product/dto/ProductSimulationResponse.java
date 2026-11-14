package dev.peluci.doceenccoreapi.product.dto;

import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.UnitCost;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import dev.peluci.doceenccoreapi.settings.TargetMargins;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ProductSimulationResponse(
        @Schema(requiredMode = REQUIRED) ProductRecipeResponse recipe,
        @Schema(requiredMode = REQUIRED) List<ProductItemResponse> items,
        @Schema(requiredMode = REQUIRED) ProductCostResponse cost,
        @Schema(requiredMode = REQUIRED) ChannelPricingResponse retail,
        @Schema(requiredMode = REQUIRED) ChannelPricingResponse wholesale) {

    public static ProductSimulationResponse of (Recipe recipe, List<ProductItemResponse> items, UnitCost cost,
                                                BigDecimal retailPrice, BigDecimal wholesalePrice,
                                                TargetMargins margins) {
        return new ProductSimulationResponse(
                ProductRecipeResponse.from(recipe),
                items,
                ProductCostResponse.from(cost),
                ChannelPricingResponse.of(cost.total(), retailPrice, margins.retail()),
                ChannelPricingResponse.of(cost.total(), wholesalePrice, margins.wholesale()));
    }
}
