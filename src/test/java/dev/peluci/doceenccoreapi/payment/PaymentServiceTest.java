package dev.peluci.doceenccoreapi.payment;

import dev.peluci.doceenccoreapi.payment.dto.PaymentRequest;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    // 22:00 on 2026-12-01 in São Paulo, which is already 2026-12-02 in UTC
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-02T01:00:00Z"),
            ZoneId.of("America/Sao_Paulo"));
    private static final BigDecimal TOTAL = new BigDecimal("100.00");

    private final OrderPaymentRepository payments = mock(OrderPaymentRepository.class);
    private final PaymentService service = new PaymentService(payments, CLOCK);

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(payments.findByOrderIdOrderByPaidOnAscCreatedAtAsc(orderId)).thenReturn(List.of());
        when(payments.save(any(OrderPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- register ----------

    @Test
    void anOmittedPaidOnIsTodayInTheBusinessZoneNotInUtc() {
        service.register(orderId, TOTAL, request("30.00", null));

        assertThat(savedPayment().getPaidOn()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void tomorrowInSaoPauloIsRejectedEvenThoughUtcAlreadyShowsThatDate() {
        assertFailsWith(() -> service.register(orderId, TOTAL, request("30.00", LocalDate.of(2026, 12, 2))),
                ErrorCode.PAYMENT_DATE_IN_FUTURE);

        verify(payments, never()).save(any());
    }

    @Test
    void aRetroactiveDateIsAccepted() {
        service.register(orderId, TOTAL, request("30.00", LocalDate.of(2026, 11, 20)));

        assertThat(savedPayment().getPaidOn()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void aPaymentUpToTheBalanceIsAcceptedAndOneCentAboveIsNot() {
        OrderPayment existing = payment(PaymentType.PAYMENT, "70.00");
        when(payments.findByOrderIdOrderByPaidOnAscCreatedAtAsc(orderId)).thenReturn(List.of(existing));

        assertFailsWith(() -> service.register(orderId, TOTAL, request("30.01", null)),
                ErrorCode.PAYMENT_EXCEEDS_BALANCE);
        verify(payments, never()).save(any());

        service.register(orderId, TOTAL, request("30.00", null));
        assertThat(savedPayment().getAmount()).isEqualTo(new BigDecimal("30.00"));
    }

    @Test
    void aVoidedPaymentDoesNotReduceTheBalance() {
        OrderPayment voided = payment(PaymentType.PAYMENT, "70.00");
        voided.voidPayment(CLOCK.instant());
        when(payments.findByOrderIdOrderByPaidOnAscCreatedAtAsc(orderId)).thenReturn(List.of(voided));

        service.register(orderId, TOTAL, request("100.00", null));

        assertThat(savedPayment().getAmount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void anAmountWithoutDecimalsIsStoredInTheMonetaryScale() {
        service.register(orderId, TOTAL, request("30", null));

        assertThat(savedPayment().getAmount()).isEqualTo(new BigDecimal("30.00"));
    }

    @Test
    void blankNotesAreStoredAsNull() {
        service.register(orderId, TOTAL,
                new PaymentRequest(new BigDecimal("30.00"), PaymentMethod.PIX, null, "   "));

        assertThat(savedPayment().getNotes()).isNull();
    }

    @Test
    void theSavedPaymentBelongsToTheOrder() {
        service.register(orderId, TOTAL, request("30.00", null));

        assertThat(savedPayment().getOrderId()).isEqualTo(orderId);
        assertThat(savedPayment().getType()).isEqualTo(PaymentType.PAYMENT);
    }

    // ---------- void ----------

    @Test
    void voidingMarksThePaymentWithTheCurrentInstant() {
        OrderPayment payment = payment(PaymentType.PAYMENT, "50.00");
        UUID paymentId = UUID.randomUUID();
        when(payments.findByIdAndOrderId(paymentId, orderId)).thenReturn(Optional.of(payment));

        service.voidPayment(orderId, paymentId);

        assertThat(payment.getVoidedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void aPaymentThatDoesNotBelongToTheOrderIsNotFound() {
        assertFailsWith(() -> service.voidPayment(orderId, UUID.randomUUID()), ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void voidingTwiceIsRejected() {
        OrderPayment payment = payment(PaymentType.PAYMENT, "50.00");
        UUID paymentId = UUID.randomUUID();
        when(payments.findByIdAndOrderId(paymentId, orderId)).thenReturn(Optional.of(payment));

        service.voidPayment(orderId, paymentId);

        assertFailsWith(() -> service.voidPayment(orderId, paymentId), ErrorCode.PAYMENT_ALREADY_VOIDED);
    }

    @Test
    void aRefundCannotBeVoided() {
        OrderPayment refund = payment(PaymentType.REFUND, "50.00");
        UUID paymentId = UUID.randomUUID();
        when(payments.findByIdAndOrderId(paymentId, orderId)).thenReturn(Optional.of(refund));

        assertFailsWith(() -> service.voidPayment(orderId, paymentId), ErrorCode.PAYMENT_NOT_ALLOWED);
    }

    // ---------- refund ----------

    @Test
    void refundNetReturnsEverythingReceivedIgnoringVoidedLinesAndIsDatedTodayLocally() {
        OrderPayment first = payment(PaymentType.PAYMENT, "40.00");
        OrderPayment second = payment(PaymentType.PAYMENT, "20.00");
        OrderPayment mistake = payment(PaymentType.PAYMENT, "99.00");
        mistake.voidPayment(CLOCK.instant());
        when(payments.findByOrderIdOrderByPaidOnAscCreatedAtAsc(orderId))
                .thenReturn(List.of(first, second, mistake));

        Optional<OrderPayment> refund = service.refundNet(orderId, PaymentMethod.CASH);

        assertThat(refund).isPresent();
        OrderPayment saved = savedPayment();
        assertThat(saved.getType()).isEqualTo(PaymentType.REFUND);
        assertThat(saved.getAmount()).isEqualTo(new BigDecimal("60.00"));
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getPaidOn()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(saved.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void nothingIsRefundedWhenNothingWasReceivedEvenWithoutAMethod() {
        assertThat(service.refundNet(orderId, null)).isEmpty();

        verify(payments, never()).save(any());
    }

    @Test
    void aRefundNeedsAMethodWhenThereIsMoneyToReturn() {
        OrderPayment received = payment(PaymentType.PAYMENT, "40.00");
        when(payments.findByOrderIdOrderByPaidOnAscCreatedAtAsc(orderId)).thenReturn(List.of(received));

        assertFailsWith(() -> service.refundNet(orderId, null), ErrorCode.REFUND_METHOD_REQUIRED);
        verify(payments, never()).save(any());
    }

    // ---------- helpers ----------

    private OrderPayment savedPayment() {
        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(payments).save(captor.capture());
        return captor.getValue();
    }

    private OrderPayment payment(PaymentType type, String amount) {
        return new OrderPayment(orderId, type, new BigDecimal(amount), PaymentMethod.PIX,
                LocalDate.of(2026, 11, 30), null);
    }

    private static PaymentRequest request(String amount, LocalDate paidOn) {
        return new PaymentRequest(new BigDecimal(amount), PaymentMethod.PIX, paidOn, null);
    }

    private static void assertFailsWith(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }
}