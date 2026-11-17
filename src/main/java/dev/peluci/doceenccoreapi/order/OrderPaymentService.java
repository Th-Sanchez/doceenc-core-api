package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.order.dto.OrderDetailResponse;
import dev.peluci.doceenccoreapi.payment.PaymentService;
import dev.peluci.doceenccoreapi.payment.dto.PaymentRequest;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class OrderPaymentService {

    private final OrderRepository orders;
    private final PaymentService payments;
    private final OrderDetailAssembler assembler;

    public OrderPaymentService(OrderRepository orders, PaymentService payments, OrderDetailAssembler assembler) {
        this.orders = orders;
        this.payments = payments;
        this.assembler = assembler;
    }

    public OrderDetailResponse register(UUID orderId, PaymentRequest request) {
        Order order = loadForPayment(orderId);
        requireAcceptsPayments(order);

        payments.register(orderId, order.getTotalAmount(), request);
        return assembler.assemble(order);
    }

    public OrderDetailResponse voidPayment(UUID orderId, UUID paymentId) {
        Order order = loadForPayment(orderId);
        requireAcceptsPayments(order);

        payments.voidPayment(orderId, paymentId);
        return assembler.assemble(order);
    }

    private Order loadForPayment(UUID orderId) {
        return orders.findWithItemsForPaymentById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND,
                        "Order not found."));
    }

    private static void requireAcceptsPayments(Order order) {
        if (!order.acceptsPayments()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_NOT_ALLOWED,
                    "Payments can only be registered or voided while an order is confirmed or delivered; "
                            + "this order is " + order.getStatus() + ".");
        }
    }
}
