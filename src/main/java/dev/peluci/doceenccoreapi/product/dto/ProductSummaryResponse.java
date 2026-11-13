package dev.peluci.doceenccoreapi.product.dto;

import dev.peluci.doceenccoreapi.product.Product;
import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.UnitCost;
import dev.peluci.doceenccoreapi.settings.TargetMargins;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ProductSummaryResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) ProductRecipeResponse recipe,
        @Schema(requiredMode = REQUIRED) BigDecimal recipeQuantity,
        @Schema(requiredMode = REQUIRED) int archivedIngredientCount,
        @Schema(requiredMode = REQUIRED) ProductCostResponse cost,
        @Schema(requiredMode = REQUIRED) ChannelPricingResponse retail,
        @Schema(requiredMode = REQUIRED) ChannelPricingResponse wholesale) {

    public static ProductSummaryResponse from(Product product, TargetMargins margins) {
        UnitCost cost = product.unitCost();
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.isActive(),
                ProductRecipeResponse.from(product.getRecipe()),
                product.getRecipeQuantity(),
                product.archivedIngredientCount(),
                ProductCostResponse.from(cost),
                ChannelPricingResponse.of(cost.total(), product.getRetailPrice(), margins.retail()),
                ChannelPricingResponse.of(cost.total(), product.getWholesalePrice(), margins.wholesale()));
    }

}
