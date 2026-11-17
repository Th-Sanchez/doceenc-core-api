package dev.peluci.doceenccoreapi.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, UUID> {

    List<OrderPayment> findByOrderIdOrderByPaidOnAscCreatedAtAsc(UUID orderId);

    Optional<OrderPayment> findByIdAndOrderId(UUID id, UUID orderId);

}
