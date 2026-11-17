package dev.peluci.doceenccoreapi.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, UUID> {

    List<OrderPayment> findByOrderIdOrderByPaidOnAscCreatedAtAsc(UUID orderId);

    Optional<OrderPayment> findByIdAndOrderId(UUID id, UUID orderId);

    interface OrderNet {
        UUID getOrderId();
        BigDecimal getNet();
    }

    @Query("""
            SELECT p.orderId AS orderId,
                    SUM(CASE WHEN p.type = :payment
                             THEN p.amount ELSE -p.amount END) AS net
            FROM OrderPayment p
            WHERE p.orderId IN :orderIds AND p.voidedAt IS NULL
            GROUP BY p.orderId""")
    List<OrderNet> netsByOrderIds(@Param("orderIds") Collection<UUID> orderIds, @Param("payment") PaymentType payment);

    default List<OrderNet> netsByOrderIds(Collection<UUID> orderIds) {
        return netsByOrderIds(orderIds, PaymentType.PAYMENT);
    }
}
