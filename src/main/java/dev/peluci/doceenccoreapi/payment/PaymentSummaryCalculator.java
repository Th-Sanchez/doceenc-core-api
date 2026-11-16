package dev.peluci.doceenccoreapi.payment;

import dev.peluci.doceenccoreapi.shared.numeric.Scale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

public final class PaymentSummaryCalculator {

    public record Entry(PaymentType type, BigDecimal amount, boolean voided) {

        public Entry {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(amount, "amount");

            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive: " + amount);
            }

            if (amount.stripTrailingZeros().scale() > Scale.MONEY) {
                throw new IllegalArgumentException("amount must have at most " + Scale.MONEY + " decimal places: " + amount);
            }
        }

    }

    public record Summary(BigDecimal paidAmount, BigDecimal balance, PaymentStatus status) {
    }

    private PaymentSummaryCalculator() {
    }

    public static Summary summarize(BigDecimal total, Collection<Entry> entries) {
        Objects.requireNonNull(entries, "entries");
        return summarize(total, net(entries));
    }

    public static Summary summarize(BigDecimal total, BigDecimal net) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(net, "net");

        if (net.signum() < 0) {
            throw new IllegalStateException("net received must not be negative: " + net);
        }

        BigDecimal paid = money(net);
        BigDecimal due = money(total);

        PaymentStatus status;
        if (paid.signum() == 0) {
            status = PaymentStatus.UNPAID;
        } else if (paid.compareTo(due) < 0) {
            status = PaymentStatus.PARTIAL;
        } else {
            status = PaymentStatus.PAID;
        }

        return new Summary(paid, due.subtract(paid), status);

    }

    public static BigDecimal net(Collection<Entry> entries) {
        Objects.requireNonNull(entries, "entries");

        BigDecimal net = BigDecimal.ZERO;
        for (Entry entry : entries) {
            if (entry.voided()) {
                continue;
            }

            net = entry.type() == PaymentType.PAYMENT
                    ? net.add(entry.amount())
                    : net.subtract(entry.amount());
        }
        return money(net);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(Scale.MONEY, RoundingMode.UNNECESSARY);
    }
}
