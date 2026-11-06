package dev.peluci.doceenccoreapi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record MeResponse(
        @Schema(requiredMode = REQUIRED) String email) {
}
