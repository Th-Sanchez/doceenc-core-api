package dev.peluci.doceenccoreapi.payment;

import dev.peluci.doceenccoreapi.order.Order;
import dev.peluci.doceenccoreapi.payment.PaymentSummaryCalculator.Entry;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_payment")
@EntityListeners(AuditingEntityListener.class)
public class OrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", insertable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentType type;

    @Column(nullable = false, precision = 14, scale = Scale.MONEY, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(length = 1000, updatable = false)
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderPayment() {
    }

    public OrderPayment(UUID orderId, PaymentType type, BigDecimal amount, PaymentMethod method, LocalDate paidOn, String notes) {
        this.orderId = orderId;
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.method = Objects.requireNonNull(method, "method");
        this.paidOn = Objects.requireNonNull(paidOn, "paidOn");
        this.notes = notes;

        new Entry(type, amount, false);
    }

    public void voidPayment(Instant now) {
        Objects.requireNonNull(now, "now");

        if (type != PaymentType.PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_NOT_ALLOWED,
                    "Only a payment can be voided; a refund cannot.");
        }

        if (voidedAt != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_ALREADY_VOIDED,
                    "This payment was already voided.");
        }

        this.voidedAt = now;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public Entry toEntry() {
        return new Entry(type, amount, isVoided());
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public PaymentType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
