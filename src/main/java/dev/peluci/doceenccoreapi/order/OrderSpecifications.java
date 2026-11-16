package dev.peluci.doceenccoreapi.order;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

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

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

}
