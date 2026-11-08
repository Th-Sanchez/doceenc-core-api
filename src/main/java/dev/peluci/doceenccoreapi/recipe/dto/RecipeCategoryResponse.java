package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.category.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record RecipeCategoryResponse(
        @Schema(requiredMode = REQUIRED) UUID id,
        @Schema(requiredMode = REQUIRED) String name) {

    static RecipeCategoryResponse from(Category category) {
        return new RecipeCategoryResponse(category.getId(), category.getName());
    }

}
