package dev.peluci.doceenccoreapi.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientPriceHistoryRepository extends JpaRepository<IngredientPriceHistory, UUID> {

    @Query("SELECT MAX(h.purchasedOn) FROM IngredientPriceHistory h WHERE h.ingredientId = :ingredientId")
    Optional<LocalDate> findLatestPurchaseDate(@Param("ingredientId") UUID ingredientId);

    List<IngredientPriceHistory> findByIngredientIdOrderByPurchasedOnDescCreatedAtDesc(UUID ingredientId);

    @Query("""
            SELECT h FROM IngredientPriceHistory h
            WHERE h.ingredientId IN :ids
                AND (SELECT COUNT(o) FROM IngredientPriceHistory o
                     WHERE o.ingredientId = h.ingredientId
                         AND (o.purchasedOn > h.purchasedOn
                              OR (o.purchasedOn = h.purchasedOn AND o.createdAt > h.createdAt))) < 2""")
    List<IngredientPriceHistory> findLatestTwoPerIngredient(@Param("ids") Collection<UUID> ids);

}
