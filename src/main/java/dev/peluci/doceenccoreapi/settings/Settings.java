package dev.peluci.doceenccoreapi.settings;

import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import dev.peluci.doceenccoreapi.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "settings")
public class Settings extends AuditableEntity {

    public static final short SINGLETON_ID = 1;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    private Short id;

    @Column(name = "target_margin_retail", nullable = false, precision = 5, scale = Scale.PERCENTAGE)
    private BigDecimal targetMarginRetail;

    @Column(name = "target_margin_wholesale", nullable = false, precision = 5, scale = Scale.PERCENTAGE)
    private BigDecimal targetMarginWholesale;

    protected Settings() {
    }

    public void changeTargetMargins(BigDecimal retail, BigDecimal wholesale) {
        this.targetMarginRetail = normalizeMargin(retail, "targetMarginRetail");
        this.targetMarginWholesale = normalizeMargin(wholesale, "targetMarginWholesale");
    }

    private static BigDecimal normalizeMargin(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        BigDecimal normalized = Scale.percentage(value);

        if (normalized.signum() < 0 || normalized.compareTo(HUNDRED) >= 0) {
            throw new IllegalArgumentException(name + " must be in [0, 100)");
        }

        return normalized;
    }

    public Short getId() {
        return id;
    }

    public BigDecimal getTargetMarginRetail() {
        return targetMarginRetail;
    }

    public BigDecimal getTargetMarginWholesale() {
        return targetMarginWholesale;
    }
}
