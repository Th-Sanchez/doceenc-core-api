package dev.peluci.doceenccoreapi.recipe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    @Query("""
            SELECT r FROM Recipe r
            JOIN FETCH r.category
            LEFT JOIN FETCH r.items i
            LEFT JOIN FETCH i.ingredient
            ORDER BY LOWER(r.name)
            """)
    List<Recipe> findAllWithItems();

    @Query("""
            SELECT r FROM Recipe r
            JOIN FETCH r.category
            LEFT JOIN FETCH r.items i
            LEFT JOIN FETCH i.ingredient
            WHERE r.id = :id
            """)
    Optional<Recipe> findWithItemsById(@Param("id") UUID id);

    @Query("SELECT r FROM Recipe r WHERE LOWER(r.name) = LOWER(:name)")
    Optional<Recipe> findByName(@Param("name") String name);
}
