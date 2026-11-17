package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.order.dto.OrderDetailResponse;
import dev.peluci.doceenccoreapi.payment.OrderPayment;
import dev.peluci.doceenccoreapi.payment.PaymentService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderDetailAssembler {

    private final PaymentService payments;

    public OrderDetailAssembler(PaymentService payments) {
        this.payments = payments;
    }

    public OrderDetailResponse assemble(Order order) {
        List<OrderPayment> ledger = order.isPending()
                ? List.of()
                : payments.ledgerOf(order.getId());

        return OrderDetailResponse.from(PricedOrder.of(order), ledger);
    }
}
