package dev.peluci.doceenccoreapi.ingredient;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IngredientSpecifications {

    private IngredientSpecifications() {
    }

    static Specification<Ingredient> filter(UUID categoryId, Boolean active, String text) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + escapeLike(text.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("brandSupplier"), "")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
