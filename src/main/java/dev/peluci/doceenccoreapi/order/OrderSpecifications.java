package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.payment.PaymentStatusSql;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    static Specification<Order> matching(OrderFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }

            if (filter.deliveryFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("deliveryDate"), filter.deliveryFrom()));
            }

            if (filter.deliveryTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("deliveryDate"), filter.deliveryTo()));
            }

            if (filter.customerId() != null) {
                predicates.add(builder.equal(root.get("customer").get("id"), filter.customerId()));
            }

            if (!filter.paymentStatus().isEmpty()) {
                Predicate[] anyStatus = filter.paymentStatus().stream()
                        .map(status -> PaymentStatusSql.matching(status,builder, query,
                                root.<UUID>get("id"), root.<BigDecimal>get("totalAmount")))
                        .toArray(Predicate[]::new);

                predicates.add(builder.and(
                        root.get("status").in(Order.PAYABLE_STATUSES),
                        builder.or(anyStatus)));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

}
