package dev.peluci.doceenccoreapi.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.recipe
            ORDER BY LOWER(p.name)
            """)
    List<Product> findAllWithRecipe();

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.recipe
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.ingredient
            WHERE p.id = :id
            """)
    Optional<Product> findWithItemsById(@Param("id") UUID id);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) = LOWER(:name)")
    Optional<Product> findByName(@Param("name") String name);
}
