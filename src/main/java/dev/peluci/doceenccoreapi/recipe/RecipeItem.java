package dev.peluci.doceenccoreapi.recipe;

import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recipe_item")
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, updatable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 14, scale = Scale.QUANTITY)
    private BigDecimal quantity;

    protected RecipeItem() {
    }

    RecipeItem(Recipe recipe, Ingredient ingredient, BigDecimal quantity) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        changeQuantity(quantity);
    }

    boolean changeQuantity(BigDecimal newQuantity) {
        Objects.requireNonNull(newQuantity, "quantity");

        if (newQuantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }

        BigDecimal normalized = Scale.quantity(newQuantity);

        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("quantity is too small");
        }

        boolean changed = this.quantity == null || this.quantity.compareTo(normalized) != 0;
        this.quantity = normalized;
        return changed;
    }

    public BigDecimal cost() {
        return RecipeCostCalculator.itemCost(quantity, ingredient.getPrice().getUnitPrice());
    }

    public UUID getId() {
        return id;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
