package dev.peluci.doceenccoreapi.product.dto;

import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.MarginStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static dev.peluci.doceenccoreapi.product.ProductPricingCalculator.ChannelPricing;
import static dev.peluci.doceenccoreapi.product.ProductPricingCalculator.channel;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ChannelPricingResponse(
        @Schema(requiredMode = REQUIRED) BigDecimal targetMarginPercent,
        @Schema(requiredMode = REQUIRED) MarginStatus status,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal price,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal grossProfit,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal marginPercent,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal suggestedPrice) {

    public static ChannelPricingResponse of(BigDecimal unitCost, BigDecimal price, BigDecimal targetMarginPercent) {
        ChannelPricing pricing = channel(unitCost, price, targetMarginPercent);
        return new ChannelPricingResponse(
                targetMarginPercent,
                pricing.status(),
                pricing.price(),
                pricing.grossProfit(),
                pricing.marginPercent(),
                pricing.suggestedPrice());
    }

}
