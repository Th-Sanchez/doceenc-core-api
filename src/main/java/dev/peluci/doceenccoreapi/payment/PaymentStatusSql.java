package dev.peluci.doceenccoreapi.payment;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.math.BigDecimal;
import java.util.UUID;

public final class PaymentStatusSql {

    private PaymentStatusSql() {
    }

    public static Predicate matching(PaymentStatus status, CriteriaBuilder builder, AbstractQuery<?> query,
                                     Expression<UUID> orderId, Expression<BigDecimal> total) {
        Expression<BigDecimal> net = netOf(builder, query, orderId);

        return switch (status) {
            case UNPAID -> builder.equal(net, BigDecimal.ZERO);
            case PARTIAL -> builder.and(
                    builder.greaterThan(net, BigDecimal.ZERO),
                    builder.lessThan(net, total));
            case PAID -> builder.and(
                    builder.greaterThan(net, BigDecimal.ZERO),
                    builder.greaterThanOrEqualTo(net, total));
        };
    }

    private static Expression<BigDecimal> netOf(CriteriaBuilder builder, AbstractQuery<?> query,
                                                Expression<UUID> orderId) {
        Subquery<BigDecimal> subquery = query.subquery(BigDecimal.class);
        Root<OrderPayment> payment = subquery.from(OrderPayment.class);

        Expression<BigDecimal> amount = payment.get("amount");
        Expression<BigDecimal> signed = builder.<BigDecimal>selectCase()
                .when(builder.equal(payment.get("type"), PaymentType.PAYMENT), amount)
                .otherwise(builder.neg(amount));

        return subquery
                .select(builder.coalesce(builder.sum(signed), BigDecimal.ZERO))
                .where(builder.equal(payment.get("orderId"), orderId),
                        builder.isNull(payment.get("voidedAt")));
    }
}
