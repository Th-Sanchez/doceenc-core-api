package dev.peluci.doceenccoreapi.product.dto;

import dev.peluci.doceenccoreapi.product.Product;
import dev.peluci.doceenccoreapi.settings.TargetMargins;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.peluci.doceenccoreapi.product.ProductPricingCalculator.*;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ProductDetailResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) boolean active,
        @Schema(requiredMode = REQUIRED) ProductRecipeResponse recipe,
        @Schema(requiredMode = REQUIRED) BigDecimal recipeQuantity,
        @Schema(requiredMode = REQUIRED) List<ProductItemResponse> items,
        @Schema(requiredMode = REQUIRED) int archivedIngredientCount,
        @Schema(requiredMode = REQUIRED) ProductCostResponse cost,
        @Schema(requiredMode = REQUIRED) ChannelPricingResponse retail,
        @Schema(requiredMode = REQUIRED) ChannelPricingResponse wholesale,
        @Schema(requiredMode = REQUIRED) Instant createdAt,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {

    public static ProductDetailResponse from(Product product, TargetMargins margins) {
        UnitCost cost = product.unitCost();
        List<ProductItemResponse> items = product.getItems().stream()
                .map(item -> ProductItemResponse.of(item.getIngredient(), item.getQuantity()))
                .sorted(ProductItemResponse.byIngredientName())
                .toList();
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.isActive(),
                ProductRecipeResponse.from(product.getRecipe()),
                product.getRecipeQuantity(),
                items,
                product.archivedIngredientCount(),
                ProductCostResponse.from(cost),
                ChannelPricingResponse.of(cost.total(), product.getRetailPrice(), margins.retail()),
                ChannelPricingResponse.of(cost.total(), product.getWholesalePrice(), margins.wholesale()),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

}
