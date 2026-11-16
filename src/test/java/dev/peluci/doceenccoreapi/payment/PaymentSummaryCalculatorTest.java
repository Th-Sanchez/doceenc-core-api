package dev.peluci.doceenccoreapi.payment;

import dev.peluci.doceenccoreapi.payment.PaymentSummaryCalculator.Entry;
import dev.peluci.doceenccoreapi.payment.PaymentSummaryCalculator.Summary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSummaryCalculatorTest {

    private static final BigDecimal TOTAL = money("100.00");

    @Test
    void noEntriesMeansUnpaid() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL, List.of());

        assertSummary(summary, "0.00", "100.00", PaymentStatus.UNPAID);
    }

    @Test
    void aPartialPaymentLeavesABalance() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL, List.of(payment("30.00")));

        assertSummary(summary, "30.00", "70.00", PaymentStatus.PARTIAL);
    }

    @Test
    void severalPaymentsAreAdded() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL,
                List.of(payment("30.00"), payment("20.50")));

        assertSummary(summary, "50.50", "49.50", PaymentStatus.PARTIAL);
    }

    @Test
    void payingExactlyTheTotalIsPaid() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL,
                List.of(payment("60.00"), payment("40.00")));

        assertSummary(summary, "100.00", "0.00", PaymentStatus.PAID);
    }

    @Test
    void anExcessIsStillPaidWithANegativeBalance() {
        // The service rejects it; the calculator must not hide it if it ever happens.
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL, List.of(payment("120.00")));

        assertSummary(summary, "120.00", "-20.00", PaymentStatus.PAID);
    }

    @Test
    void voidedEntriesAreIgnored() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL,
                List.of(voided(payment("100.00")), payment("40.00")));

        assertSummary(summary, "40.00", "60.00", PaymentStatus.PARTIAL);
    }

    @Test
    void onlyVoidedEntriesMeansUnpaid() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL, List.of(voided(payment("100.00"))));

        assertSummary(summary, "0.00", "100.00", PaymentStatus.UNPAID);
    }

    @Test
    void aRefundSubtractsFromTheNet() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL,
                List.of(payment("100.00"), refund("30.00")));

        assertSummary(summary, "70.00", "30.00", PaymentStatus.PARTIAL);
    }

    @Test
    void aFullRefundBringsTheNetBackToZero() {
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL,
                List.of(payment("40.00"), payment("20.00"), refund("60.00")));

        assertSummary(summary, "0.00", "100.00", PaymentStatus.UNPAID);
    }

    @Test
    void aVoidedPaymentIsNotRefundedTwice() {
        // 40 was typed wrong and voided, 25 is the real payment, and the cancellation refunds 25.
        Summary summary = PaymentSummaryCalculator.summarize(TOTAL,
                List.of(voided(payment("40.00")), payment("25.00"), refund("25.00")));

        assertThat(summary.paidAmount()).isEqualTo(money("0.00"));
        assertThat(summary.status()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void theNetOverloadClassifiesLikeTheEntriesOne() {
        assertSummary(PaymentSummaryCalculator.summarize(TOTAL, money("0.00")), "0.00", "100.00", PaymentStatus.UNPAID);
        assertSummary(PaymentSummaryCalculator.summarize(TOTAL, money("40.00")), "40.00", "60.00", PaymentStatus.PARTIAL);
        assertSummary(PaymentSummaryCalculator.summarize(TOTAL, money("100.00")), "100.00", "0.00", PaymentStatus.PAID);
    }

    @Test
    void resultsAlwaysUseTheMonetaryScale() {
        Summary summary = PaymentSummaryCalculator.summarize(money("100"), money("0"));

        assertThat(summary.paidAmount().scale()).isEqualTo(2);
        assertThat(summary.balance().scale()).isEqualTo(2);
        assertThat(PaymentSummaryCalculator.net(List.of()).scale()).isEqualTo(2);
    }

    @Test
    void aNegativeNetIsABrokenLedgerAndFailsLoudly() {
        assertThatThrownBy(() -> PaymentSummaryCalculator.summarize(TOTAL,
                List.of(payment("10.00"), refund("30.00"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void entriesRejectNonPositiveAmounts() {
        assertThatThrownBy(() -> new Entry(PaymentType.PAYMENT, money("0.00"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Entry(PaymentType.REFUND, money("-5.00"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entriesRejectMoreDecimalsThanTheMonetaryScaleButAcceptTrailingZeros() {
        assertThatThrownBy(() -> new Entry(PaymentType.PAYMENT, money("10.005"), false))
                .isInstanceOf(IllegalArgumentException.class);

        Summary summary = PaymentSummaryCalculator.summarize(TOTAL, List.of(payment("10.500")));
        assertSummary(summary, "10.50", "89.50", PaymentStatus.PARTIAL);
    }

    private static void assertSummary(Summary summary, String paid, String balance, PaymentStatus status) {
        assertThat(summary.paidAmount()).isEqualTo(money(paid));
        assertThat(summary.balance()).isEqualTo(money(balance));
        assertThat(summary.status()).isEqualTo(status);
    }

    private static Entry payment(String amount) {
        return new Entry(PaymentType.PAYMENT, money(amount), false);
    }

    private static Entry refund(String amount) {
        return new Entry(PaymentType.REFUND, money(amount), false);
    }

    private static Entry voided(Entry entry) {
        return new Entry(entry.type(), entry.amount(), true);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}