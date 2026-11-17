package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.payment.PaymentMethod;
import dev.peluci.doceenccoreapi.payment.PaymentService;
import dev.peluci.doceenccoreapi.payment.dto.PaymentRequest;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderPaymentServiceTest {

    private static final BigDecimal TOTAL = new BigDecimal("100.00");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final OrderDetailAssembler assembler = mock(OrderDetailAssembler.class);
    private final OrderPaymentService service = new OrderPaymentService(orders, payments, assembler);

    private final UUID orderId = UUID.randomUUID();
    private final Order order = mock(Order.class);
    private final PaymentRequest request =
            new PaymentRequest(new BigDecimal("30.00"), PaymentMethod.PIX, null, null);

    @BeforeEach
    void setUp() {
        when(order.acceptsPayments()).thenReturn(true);
        when(order.getTotalAmount()).thenReturn(TOTAL);
        when(orders.findWithItemsForPaymentById(orderId)).thenReturn(Optional.of(order));
    }

    @Test
    void registerDelegatesToTheLedgerWithTheOrdersTotalAndAssemblesTheDetail() {
        service.register(orderId, request);

        verify(payments).register(orderId, TOTAL, request);
        verify(assembler).assemble(order);
    }

    @Test
    void registerIsRejectedWhenTheOrderDoesNotAcceptPaymentsAndTheLedgerIsNotTouched() {
        when(order.acceptsPayments()).thenReturn(false);

        assertFailsWith(() -> service.register(orderId, request), ErrorCode.PAYMENT_NOT_ALLOWED);

        verifyNoInteractions(payments);
    }

    @Test
    void registerOnAnUnknownOrderIsNotFound() {
        assertFailsWith(() -> service.register(UUID.randomUUID(), request), ErrorCode.ORDER_NOT_FOUND);

        verifyNoInteractions(payments);
    }

    @Test
    void voidDelegatesToTheLedgerAndAssemblesTheDetail() {
        UUID paymentId = UUID.randomUUID();

        service.voidPayment(orderId, paymentId);

        verify(payments).voidPayment(orderId, paymentId);
        verify(assembler).assemble(order);
    }

    @Test
    void voidIsRejectedWhenTheOrderDoesNotAcceptPaymentsAndTheLedgerIsNotTouched() {
        when(order.acceptsPayments()).thenReturn(false);

        assertFailsWith(() -> service.voidPayment(orderId, UUID.randomUUID()), ErrorCode.PAYMENT_NOT_ALLOWED);

        verifyNoInteractions(payments);
    }

    @Test
    void voidOnAnUnknownOrderIsNotFound() {
        assertFailsWith(() -> service.voidPayment(UUID.randomUUID(), UUID.randomUUID()),
                ErrorCode.ORDER_NOT_FOUND);
    }

    private static void assertFailsWith(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }
}