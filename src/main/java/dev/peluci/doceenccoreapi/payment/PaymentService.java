package dev.peluci.doceenccoreapi.payment;

import dev.peluci.doceenccoreapi.payment.PaymentSummaryCalculator.Entry;
import dev.peluci.doceenccoreapi.payment.PaymentSummaryCalculator.Summary;
import dev.peluci.doceenccoreapi.payment.dto.PaymentRequest;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {

    private final OrderPaymentRepository payments;
    private final Clock clock;

    public PaymentService(OrderPaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    public OrderPayment register(UUID orderId, BigDecimal orderTotal, PaymentRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate paidOn = request.paidOn() != null ? request.paidOn() : today;

        if (paidOn.isAfter(today)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PAYMENT_DATE_IN_FUTURE,
                    "The payment date must not be in the future.");
        }

        BigDecimal amount = request.amount().setScale(Scale.MONEY, RoundingMode.UNNECESSARY);

        Summary summary = PaymentSummaryCalculator.summarize(orderTotal, entriesOf(orderId));
        if (amount.compareTo(summary.balance()) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PAYMENT_EXCEEDS_BALANCE,
                    "The payment of " + amount.toPlainString() + " exceeds the remaining balance of "
                            + summary.balance().toPlainString() + ".");
        }

        return payments.save(new OrderPayment(orderId, PaymentType.PAYMENT, amount, request.method(), paidOn,
                blankToNull(request.notes())));
    }

    public void voidPayment(UUID orderId, UUID paymentId) {
        OrderPayment payment = payments.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                        "Payment not found."));

        payment.voidPayment(clock.instant());
    }

    public Optional<OrderPayment> refundNet(UUID orderId, PaymentMethod refundMethod) {
        BigDecimal net = PaymentSummaryCalculator.net(entriesOf(orderId));

        if (net.signum() < 0) {
            throw new IllegalStateException("The ledger of order " + orderId + " has a negative net: " + net);
        }

        if (net.signum() == 0) {
            return Optional.empty();
        }

        if (refundMethod == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.REFUND_METHOD_REQUIRED,
                    "A refund method is required to return " + net.toPlainString() + " to the customer.");
        }

        return Optional.of(payments.save(new OrderPayment(orderId, PaymentType.REFUND, net, refundMethod,
                LocalDate.now(clock), null)));

    }

    public Map<UUID, BigDecimal> netsOf(Collection<UUID> orderIds) {
        Map<UUID, BigDecimal> nets = new HashMap<>();
        orderIds.forEach(id -> nets.put(id, BigDecimal.ZERO.setScale(Scale.MONEY)));

        if (!orderIds.isEmpty()) {
            payments.netsByOrderIds(orderIds).forEach(row -> nets.put(row.getOrderId(), row.getNet()));
        }
        return nets;
    }

    public List<OrderPayment> ledgerOf(UUID orderId) {
        return payments.findByOrderIdOrderByPaidOnAscCreatedAtAsc(orderId);
    }

    private List<Entry> entriesOf(UUID orderId) {
        return ledgerOf(orderId).stream().map(OrderPayment::toEntry).toList();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
