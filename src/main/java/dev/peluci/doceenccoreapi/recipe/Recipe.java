package dev.peluci.doceenccoreapi.recipe;

import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import dev.peluci.doceenccoreapi.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "recipe")
public class Recipe extends AuditableEntity {

    public record ItemDraft(Ingredient ingredient, BigDecimal quantity) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "yield_quantity", nullable = false, precision = 14, scale = Scale.QUANTITY)
    private BigDecimal yieldQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "yield_unit", nullable = false, updatable = false, length = 20)
    private BaseUnit yieldUnit;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeItem> items = new ArrayList<>();

    protected Recipe() {
    }

    public Recipe(String name, Category category, BigDecimal yieldQuantity, BaseUnit yieldUnit, Integer prepTimeMinutes, Integer shelfLifeDays) {
        this.yieldUnit = Objects.requireNonNull(yieldUnit, "yieldUnit");
        update(name, category, yieldQuantity, prepTimeMinutes, shelfLifeDays);
    }

    public void update(String name, Category category, BigDecimal yieldQuantity,
                       Integer prepTimeMinutes, Integer shelfLifeDays) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(yieldQuantity, "yieldQuantity");

        if (yieldQuantity.signum() <= 0 || Scale.quantity(yieldQuantity).signum() <= 0) {
            throw new IllegalArgumentException("yieldQuantity must be positive");
        }
        if (prepTimeMinutes != null && prepTimeMinutes <= 0) {
            throw new IllegalArgumentException("prepTimeMinutes must be positive");
        }
        if (shelfLifeDays != null && shelfLifeDays <= 0) {
            throw new IllegalArgumentException("shelfLifeDays must be positive");
        }

        this.name = name;
        this.category = category;
        this.yieldQuantity = Scale.quantity(yieldQuantity);
        this.prepTimeMinutes = prepTimeMinutes;
        this.shelfLifeDays = shelfLifeDays;
    }

    public void replaceItems(Collection<ItemDraft> drafts) {
        Objects.requireNonNull(drafts, "drafts");
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("a recipe needs at least one item");
        }

        Map<UUID, ItemDraft> wanted = new LinkedHashMap<>();
        for (ItemDraft draft : drafts) {
            if (wanted.put(draft.ingredient.getId(), draft) != null) {
                throw new IllegalArgumentException("duplicate ingredient in recipe items");
            }
        }

        items.removeIf(item -> !wanted.containsKey(item.getIngredient().getId()));

        Set<UUID> existing = new HashSet<>();
        for (RecipeItem item : items) {
            UUID ingredientId = item.getIngredient().getId();
            item.changeQuantity(wanted.get(ingredientId).quantity());
            existing.add(ingredientId);
        }

        wanted.forEach((ingredientId, draft) -> {
            if (!existing.contains(ingredientId)) {
                items.add(new RecipeItem(this, draft.ingredient(), draft.quantity()));
            }
        });
    }

    public RecipeCostCalculator.Summary cost() {
        List<BigDecimal> itemCosts = items.stream().map(RecipeItem::cost).toList();
        return RecipeCostCalculator.summarize(itemCosts, yieldQuantity);
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

    public Category getCategory() {
        return category;
    }

    public BigDecimal getYieldQuantity() {
        return yieldQuantity;
    }

    public BaseUnit getYieldUnit() {
        return yieldUnit;
    }

    public Integer getPrepTimeMinutes() {
        return prepTimeMinutes;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public boolean isActive() {
        return active;
    }

    public List<RecipeItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
