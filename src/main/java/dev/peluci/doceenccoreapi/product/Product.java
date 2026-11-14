package dev.peluci.doceenccoreapi.product;

import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.UnitCost;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import dev.peluci.doceenccoreapi.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product extends AuditableEntity {

    public record ItemDraft(Ingredient ingredient, BigDecimal quantity) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "recipe_quantity", nullable = false, precision = 14, scale = Scale.QUANTITY)
    private BigDecimal recipeQuantity;

    @Column(name = "retail_price", precision = 12, scale = Scale.MONEY)
    private BigDecimal retailPrice;

    @Column(name = "wholesale_price", precision = 12, scale = Scale.MONEY)
    private BigDecimal wholesalePrice;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductItem> items = new ArrayList<>();

    protected Product() {
    }

    public Product(String name, Recipe recipe, BigDecimal recipeQuantity,
                   BigDecimal retailPrice, BigDecimal wholesalePrice) {
        update(name, recipe, recipeQuantity, retailPrice, wholesalePrice);
    }

    public void update(String name, Recipe recipe, BigDecimal recipeQuantity,
                       BigDecimal retailPrice, BigDecimal wholesalePrice) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(recipeQuantity, "recipeQuantity");

        if (recipeQuantity.signum() <= 0 || Scale.quantity(recipeQuantity).signum() <= 0) {
            throw new IllegalArgumentException("recipeQuantity must be positive");
        }

        this.name = name;
        this.recipe = recipe;
        this.recipeQuantity = recipeQuantity;
        this.retailPrice = normalizePrice(retailPrice, "retailPrice");
        this.wholesalePrice = normalizePrice(wholesalePrice, "wholesalePrice");
    }

    private static BigDecimal normalizePrice(BigDecimal price, String field) {
        if (price == null) {
            return null;
        }

        BigDecimal normalized = Scale.money(price);

        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }

        return normalized;
    }

    public boolean replaceItems(Collection<ItemDraft> drafts) {
        Objects.requireNonNull(drafts, "drafts");

        Map<UUID, ItemDraft> wanted = new LinkedHashMap<>();
        for (ItemDraft draft : drafts) {
            if (wanted.put(draft.ingredient().getId(), draft) != null) {
                throw new IllegalArgumentException("duplicate ingredient in product items");
            }
        }

        boolean changed = items.removeIf(item -> !wanted.containsKey(item.getIngredient().getId()));

        Set<UUID> existing = new HashSet<>();
        for (ProductItem item : items) {
            UUID ingredientId = item.getIngredient().getId();
            changed |= item.changeQuantity(wanted.get(ingredientId).quantity());
            existing.add(ingredientId);
        }

        for (Map.Entry<UUID, ItemDraft> entry : wanted.entrySet()) {
            if (!existing.contains(entry.getKey())) {
                ItemDraft draft = entry.getValue();
                items.add(new ProductItem(this, draft.ingredient(), draft.quantity()));
                changed = true;
            }
        }

        return changed;
    }

    public UnitCost unitCost() {
        return ProductPricingCalculator.unitCost(
                recipe.cost().totalCost(),
                recipe.getYieldQuantity(),
                recipeQuantity,
                items.stream().map(ProductItem::cost).toList());
    }

    public int archivedIngredientCount() {
        long inRecipe = recipe.getItems().stream().filter(item -> !item.getIngredient().isActive()).count();
        long inExtras = items.stream().filter(item -> !item.getIngredient().isActive()).count();
        return (int) (inRecipe + inExtras);
    }

    public void archive() {
        this.active = false;
    }

    public void restore() {
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public BigDecimal getRecipeQuantity() {
        return recipeQuantity;
    }

    public BigDecimal getRetailPrice() {
        return retailPrice;
    }

    public BigDecimal getWholesalePrice() {
        return wholesalePrice;
    }

    public boolean isActive() {
        return active;
    }

    public List<ProductItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
