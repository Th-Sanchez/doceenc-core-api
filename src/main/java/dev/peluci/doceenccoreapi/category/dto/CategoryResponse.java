package dev.peluci.doceenccoreapi.category.dto;

import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryType;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, CategoryType type) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType());
    }

}
