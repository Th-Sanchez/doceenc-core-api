package dev.peluci.doceenccoreapi.settings;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record SettingsResponse(
        @Schema(requiredMode = REQUIRED) BigDecimal targetMarginRetail,
        @Schema(requiredMode = REQUIRED) BigDecimal targetMarginWholesale,
        @Schema(requiredMode = REQUIRED) Instant updatedAt) {

    public static SettingsResponse from(Settings settings) {
        return new SettingsResponse(
                settings.getTargetMarginRetail(),
                settings.getTargetMarginWholesale(),
                settings.getUpdatedAt());
    }
}
