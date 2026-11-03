package dev.peluci.doceenccoreapi.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ingredient_price_history")
@EntityListeners(AuditingEntityListener.class)
public class IngredientPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    private Ingredient ingredient;

    @Column(name = "ingredient_id", insertable = false, updatable = false)
    private UUID ingredientId;

    @Embedded
    private IngredientPrice price;

    @Column(name = "purchased_on", nullable = false, updatable = false)
    private LocalDate purchasedOn;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IngredientPriceHistory() {
    }

    public IngredientPriceHistory(Ingredient ingredient, IngredientPrice price, LocalDate purchasedOn) {
        this.ingredient = ingredient;
        this.ingredientId = ingredient.getId();
        this.price = price;
        this.purchasedOn = purchasedOn;
    }

    public UUID getId() {
        return id;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }

    public IngredientPrice getPrice() {
        return price;
    }

    public LocalDate getPurchasedOn() {
        return purchasedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
