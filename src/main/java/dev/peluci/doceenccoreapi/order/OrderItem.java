package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.product.Product;
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
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 12, scale = Scale.MONEY)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", precision = 12, scale = Scale.MONEY)
    private BigDecimal unitCost;

    @Column(name = "product_name", length = 120)
    private String productName;

    protected OrderItem() {
    }

    OrderItem(Order order, Product product, int quantity) {
        this.order = Objects.requireNonNull(order, "order");
        this.product = Objects.requireNonNull(product, "product");
        requirePositive(quantity);
        this.quantity = quantity;
    }

    boolean changeQuantity(int newQuantity) {
        requirePositive(newQuantity);

        if(quantity == newQuantity) {
            return false;
        }

        this.quantity = newQuantity;

        return true;
    }

    void freeze(BigDecimal unitPrice, BigDecimal unitCost, String productName) {
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        this.unitCost = Objects.requireNonNull(unitCost, "unitCost");
        this.productName = Objects.requireNonNull(productName, "productName");
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    public boolean isFrozen() {
        return unitPrice != null;
    }

    public BigDecimal frozenSubtotal() {
        return isFrozen() ? Scale.money(unitPrice.multiply(BigDecimal.valueOf(quantity))) : null;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getProductName() {
        return productName;
    }
}
