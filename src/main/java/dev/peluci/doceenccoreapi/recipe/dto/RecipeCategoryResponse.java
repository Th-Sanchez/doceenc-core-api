package dev.peluci.doceenccoreapi.recipe.dto;

import dev.peluci.doceenccoreapi.category.Category;

import java.util.UUID;

public record RecipeCategoryResponse(UUID id, String name) {

    static RecipeCategoryResponse from(Category category) {
        return new RecipeCategoryResponse(category.getId(), category.getName());
    }

}
