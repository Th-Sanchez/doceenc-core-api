package dev.peluci.doceenccoreapi.ingredient;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    @Query("""
            SELECT i FROM Ingredient i 
            WHERE LOWER(i.name) = LOWER(:name)
                AND LOWER(COALESCE(i.brandSupplier, '')) = LOWER(:brand)""")
    Optional<Ingredient> findByIdentity(@Param("name") String name, @Param("brand") String brand);

    @EntityGraph(attributePaths = "category")
    Optional<Ingredient> findWithCategoryById(UUID id);

}
