package dev.peluci.doceenccoreapi.payment;

import dev.peluci.doceenccoreapi.order.Order;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderPaymentTest {

    private static final Instant NOW = Instant.parse("2026-12-01T15:00:00Z");

    private final Order order = mock(Order.class);

    @Test
    void aPaymentCanBeVoidedOnce() {
        OrderPayment payment = payment(PaymentType.PAYMENT, "50.00");

        payment.voidPayment(NOW);

        assertThat(payment.isVoided()).isTrue();
        assertThat(payment.getVoidedAt()).isEqualTo(NOW);
        assertThat(payment.toEntry().voided()).isTrue();

        assertThatThrownBy(() -> payment.voidPayment(NOW.plusSeconds(60)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_VOIDED));
        assertThat(payment.getVoidedAt()).isEqualTo(NOW);
    }

    @Test
    void aRefundCannotBeVoided() {
        OrderPayment refund = payment(PaymentType.REFUND, "50.00");

        assertThatThrownBy(() -> refund.voidPayment(NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYMENT_NOT_ALLOWED));
        assertThat(refund.isVoided()).isFalse();
    }

    @Test
    void aNewPaymentIsNotVoidedAndMapsToALedgerEntry() {
        OrderPayment payment = payment(PaymentType.PAYMENT, "50.00");

        assertThat(payment.isVoided()).isFalse();
        assertThat(payment.toEntry())
                .isEqualTo(new PaymentSummaryCalculator.Entry(PaymentType.PAYMENT, new BigDecimal("50.00"), false));
    }

    @Test
    void invalidAmountsAreRejectedOnConstruction() {
        assertThatThrownBy(() -> payment(PaymentType.PAYMENT, "0.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment(PaymentType.PAYMENT, "10.005"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OrderPayment payment(PaymentType type, String amount) {
        return new OrderPayment(order, type, new BigDecimal(amount), PaymentMethod.PIX,
                LocalDate.of(2026, 12, 1), null);
    }
}
