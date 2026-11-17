package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Line;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.LineStatus;
import dev.peluci.doceenccoreapi.order.OrderPricingCalculator.Result;
import dev.peluci.doceenccoreapi.product.Product;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "orders")
public class Order extends AuditableEntity {

    public record ItemDraft(Product product, int quantity) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "order_number", insertable = false, updatable = false)
    private Long orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 20)
    private FulfillmentType fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_channel", length = 20)
    private PriceChannel priceChannel;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "total_amount", precision = 14, scale = Scale.MONEY)
    private BigDecimal totalAmount;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(Customer customer, FulfillmentType fulfillmentType, String deliveryAddress,
                 LocalDate deliveryDate, String notes) {
        applyDetails(customer, fulfillmentType, deliveryAddress, deliveryDate, notes);
    }

    public void update(Customer customer, FulfillmentType fulfillmentType, String deliveryAddress,
                 LocalDate deliveryDate, String notes) {
        assertEditable();
        applyDetails(customer, fulfillmentType, deliveryAddress, deliveryDate, notes);
    }

    private void applyDetails(Customer customer, FulfillmentType fulfillmentType, String requestedAddress,
                              LocalDate deliveryDate, String notes) {
        this.customer = Objects.requireNonNull(customer, "customer");
        this.fulfillmentType = Objects.requireNonNull(fulfillmentType, "fulfillmentType");
        this.deliveryAddress = resolveDeliveryAddress(fulfillmentType, requestedAddress, customer);
        this.deliveryDate = deliveryDate;
        this.notes = notes;
    }

    private static String resolveDeliveryAddress(FulfillmentType type, String requested, Customer customer) {
        if (type == FulfillmentType.PICKUP) {
            return null;
        }

        String address = requested != null ? requested : blankToNull(customer.getAddress());
        if (address == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.DELIVERY_ADDRESS_REQUIRED,
                    "A delivery address is required: inform one or register it on the customer.");
        }

        return address;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public boolean replaceItems(Collection<ItemDraft> drafts) {
        Objects.requireNonNull(drafts, "drafts");
        assertEditable();

        Map<UUID, ItemDraft> wanted = new LinkedHashMap<>();
        for (ItemDraft draft : drafts) {
            if (wanted.put(draft.product().getId(), draft) != null) {
                throw new IllegalArgumentException("duplicate product in order items");
            }
        }

        boolean changed = items.removeIf(item -> !wanted.containsKey(item.getProduct().getId()));

        Set<UUID> existing = new HashSet<>();
        for (OrderItem item : items) {
            UUID productId = item.getProduct().getId();
            changed |= item.changeQuantity(wanted.get(productId).quantity());
            existing.add(productId);
        }

        for (Map.Entry<UUID, ItemDraft> entry : wanted.entrySet()) {
            if (!existing.contains(entry.getKey())) {
                ItemDraft draft = entry.getValue();
                items.add(new OrderItem(this, draft.product(), draft.quantity()));
                changed = true;
            }
        }

        return changed;
    }

    public void assertCanConfirm() {
        requireTransition(OrderStatus.CONFIRMED, OrderStatus.PENDING);

        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ORDER_ITEMS_REQUIRED,
                    "An order needs at least one item to be confirmed.");
        }

        if (deliveryDate == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.DELIVERY_DATE_REQUIRED,
                    "A delivery date is required to confirm the order.");
        }

        if (fulfillmentType == FulfillmentType.DELIVERY && deliveryAddress == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.DELIVERY_ADDRESS_REQUIRED,
                    "A delivery address is required to confirm a delivery order.");
        }
    }

    public void confirm(Instant now, PriceChannel channel, Result pricing) {
        assertCanConfirm();
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(pricing, "pricing");

        if (pricing.lines().size() != items.size()) {
            throw new IllegalArgumentException("pricing lines do not match the order items");
        }

        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            Line line = pricing.lines().get(i);

            if (line.status() != LineStatus.PRICED) {
                throw new IllegalArgumentException("every line must be priced to confirm");
            }

            item.freeze(line.unitPrice(), line.unitCost(), item.getProduct().getName());
        }

        this.priceChannel = channel;
        this.totalAmount = pricing.total();
        this.confirmedAt = now;
        this.status = OrderStatus.CONFIRMED;
    }

    public void deliver(Instant now) {
        requireTransition(OrderStatus.DELIVERED, OrderStatus.CONFIRMED);
        this.deliveredAt = Objects.requireNonNull(now, "now");
        this.status = OrderStatus.DELIVERED;
    }

    public void cancel(Instant now) {
        requireTransition(OrderStatus.CANCELLED, OrderStatus.PENDING, OrderStatus.CONFIRMED);
        this.cancelledAt = Objects.requireNonNull(now, "now");
        this.status = OrderStatus.CANCELLED;
    }

    public void assertEditable() {
        if (status != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ORDER_NOT_EDITABLE,
                    "Only a pending order can be changed.");
        }
    }

    private void requireTransition(OrderStatus target, OrderStatus... allowedFrom) {
        for (OrderStatus allowed : allowedFrom) {
            if (status == allowed) {
                return;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ORDER_TRANSITION,
                "An order that is " + status + " cannot become " + target + ".");
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public boolean acceptsPayments() {
        return status == OrderStatus.CONFIRMED || status == OrderStatus.DELIVERED;
    }

    public UUID getId() {
        return id;
    }

    public Long getOrderNumber() {
        return orderNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    public PriceChannel getPriceChannel() {
        return priceChannel;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}