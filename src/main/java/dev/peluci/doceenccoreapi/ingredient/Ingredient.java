package dev.peluci.doceenccoreapi.ingredient;

import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "ingredient")
public class Ingredient extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "brand_supplier", length = 120)
    private String brandSupplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", nullable = false, updatable = false, length = 20)
    private BaseUnit baseUnit;

    @Embedded
    private IngredientPrice price;

    @Column(nullable = false)
    private boolean active = true;

    protected Ingredient() {
    }

    public Ingredient(String name, String brandSupplier, Category category, BaseUnit baseUnit, IngredientPrice price) {
        this.name = name;
        this.brandSupplier = brandSupplier;
        this.category = category;
        this.baseUnit = baseUnit;
        this.price = price;
    }

    public void update(String name, String brandSupplier, Category category) {
        this.name = name;
        this.brandSupplier = brandSupplier;
        this.category = category;
    }

    public void changePrice(IngredientPrice newPrice) {
        this.price = newPrice;
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

    public String getBrandSupplier() {
        return brandSupplier;
    }

    public Category getCategory() {
        return category;
    }

    public BaseUnit getBaseUnit() {
        return baseUnit;
    }

    public IngredientPrice getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
