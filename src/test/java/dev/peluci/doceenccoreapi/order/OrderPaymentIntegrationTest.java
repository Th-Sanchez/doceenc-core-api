package dev.peluci.doceenccoreapi.order;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OrderPaymentIntegrationTest extends OrderApiTestSupport {

    @Test
    void aConfirmedOrderStartsUnpaidWithTheFullBalance() throws Exception {
        UUID id = confirmedOrder();

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.payments.length()").value(0))
                .andExpect(jsonPath("$.paidAmount").value(0.0))
                .andExpect(jsonPath("$.balance").value(150.0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    @Test
    void pendingAndCancelledOrdersShowNoPaymentSummary() throws Exception {
        UUID pending = pendingOrder();
        doGet(BASE + "/" + pending)
                .andExpect(jsonPath("$.payments.length()").value(0))
                .andExpect(jsonPath("$.paidAmount").doesNotExist())
                .andExpect(jsonPath("$.balance").doesNotExist())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());

        UUID cancelled = confirmedOrder();
        doPost(BASE + "/" + cancelled + "/cancel", "").andExpect(status().isOk());
        doGet(BASE + "/" + cancelled)
                .andExpect(jsonPath("$.paidAmount").doesNotExist())
                .andExpect(jsonPath("$.balance").doesNotExist())
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());
    }

    @Test
    void confirmingWithAPartialInitialPaymentRegistersItInTheSameCall() throws Exception {
        UUID id = pendingOrder();

        doPost(BASE + "/" + id + "/confirm", confirmBody("50.00", "PIX", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paidAmount").value(50.0))
                .andExpect(jsonPath("$.balance").value(100.0))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].id").isString())
                .andExpect(jsonPath("$.payments[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.payments[0].amount").value(50.0))
                .andExpect(jsonPath("$.payments[0].method").value("PIX"))
                .andExpect(jsonPath("$.payments[0].paidOn").value(today().toString()))
                .andExpect(jsonPath("$.payments[0].notes").value("Sinal"))
                .andExpect(jsonPath("$.payments[0].voidedAt").doesNotExist());
    }

    @Test
    void confirmingWithTheFullAmountAsInitialPaymentMakesTheOrderPaid() throws Exception {
        UUID id = pendingOrder();

        doPost(BASE + "/" + id + "/confirm", confirmBody("150.00", "CASH", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(150.0))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void anInvalidInitialPaymentLeavesTheOrderPendingAndWritesNothing() throws Exception {
        UUID id = pendingOrder();

        doPost(BASE + "/" + id + "/confirm", confirmBody("150.01", "PIX", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));

        doPost(BASE + "/" + id + "/confirm", confirmBody("50.00", "PIX", tomorrow().toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_DATE_IN_FUTURE"));

        doPost(BASE + "/" + id + "/confirm", """
                {"initialPayment":{"amount":0,"method":"PIX"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'initialPayment.amount')]").exists());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.payments.length()").value(0));

        // nothing was left behind: confirming later starts from an empty ledger
        doPost(BASE + "/" + id + "/confirm", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    @Test
    void aPaymentIsRegisteredAndTheResponseCarriesTheNewBalance() throws Exception {
        UUID id = confirmedOrder();

        doPost(BASE + "/" + id + "/payments", paymentJson("50.00", "PIX", null, "Sinal"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/" + id)))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.paidAmount").value(50.0))
                .andExpect(jsonPath("$.balance").value(100.0))
                .andExpect(jsonPath("$.payments.length()").value(1));

        doPost(BASE + "/" + id + "/payments", paymentJson("100.00", "CASH", null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(150.0))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].amount").value(50.0))
                .andExpect(jsonPath("$.payments[1].amount").value(100.0))
                .andExpect(jsonPath("$.payments[1].method").value("CASH"))
                .andExpect(jsonPath("$.payments[1].notes").doesNotExist());
    }

    @Test
    void aPaymentAboveTheBalanceIsRejectedButTheExactBalanceIsAccepted() throws Exception {
        UUID id = confirmedOrder();
        payOk(id, "100.00");

        pay(id, "50.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.balance").value(50.0));

        pay(id, "50.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void paymentsAreOnlyAcceptedOnConfirmedOrDeliveredOrders() throws Exception {
        UUID pending = pendingOrder();
        pay(pending, "10.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));

        UUID cancelled = confirmedOrder();
        doPost(BASE + "/" + cancelled + "/cancel", "").andExpect(status().isOk());
        pay(cancelled, "10.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));

        doGet(BASE + "/" + pending).andExpect(jsonPath("$.payments.length()").value(0));
        doGet(BASE + "/" + cancelled).andExpect(jsonPath("$.payments.length()").value(0));

        pay(UUID.randomUUID(), "10.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void theBalanceCanBeReceivedAtDelivery() throws Exception {
        UUID id = confirmedOrder();
        payOk(id, "50.00");
        doPost(BASE + "/" + id + "/deliver", "").andExpect(status().isOk());

        pay(id, "100.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.payments.length()").value(2));

        pay(id, "0.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));
    }

    @Test
    void invalidPaymentBodiesAreRejectedWithTheFieldPath() throws Exception {
        UUID id = confirmedOrder();

        for (String amount : List.of("0", "-5.00", "12.345")) {
            doPost(BASE + "/" + id + "/payments", paymentJson(amount, "PIX", null, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists());
        }

        doPost(BASE + "/" + id + "/payments", paymentJson("10.00", null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'method')]").exists());

        doPost(BASE + "/" + id + "/payments", """
                {"amount":10.00,"method":"BITCOIN"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        doGet(BASE + "/" + id).andExpect(jsonPath("$.payments.length()").value(0));
    }

    @Test
    void aFuturePaymentDateIsRejectedAnOmittedOneIsTodayAndARetroactiveOneIsKept() throws Exception {
        UUID id = confirmedOrder();

        pay(id, "10.00", tomorrow().toString())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_DATE_IN_FUTURE"));

        pay(id, "10.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments[0].paidOn").value(today().toString()));

        String retroactive = today().minusDays(3).toString();

        pay(id, "10.00", retroactive)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].paidOn").value(retroactive));
    }

    @Test
    void voidingATypoRestoresTheBalanceAndKeepsTheLine() throws Exception {
        UUID id = confirmedOrder();
        UUID mistake = paymentIdAt(payOk(id, "100.00"), 0);

        voidPayment(id, mistake)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(0.0))
                .andExpect(jsonPath("$.balance").value(150.0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].voidedAt").isString());

        pay(id, "15.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidAmount").value(15.0))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    void aPaymentCanOnlyBeVoidedOnce() throws Exception {
        UUID id = confirmedOrder();
        UUID paymentId = paymentIdAt(payOk(id, "50.00"), 0);

        voidPayment(id, paymentId).andExpect(status().isOk());
        voidPayment(id, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_VOIDED"));
    }

    @Test
    void voidingIsStillAllowedAfterDelivery() throws Exception {
        UUID id = confirmedOrder();
        UUID paymentId = paymentIdAt(payOk(id, "150.00"), 0);
        doPost(BASE + "/" + id + "/deliver", "").andExpect(status().isOk());

        voidPayment(id, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    @Test
    void anUnknownPaymentOrOneFromAnotherOrderIsNotFound() throws Exception {
        UUID id = confirmedOrder();
        UUID other = confirmedOrder();
        UUID otherPayment = paymentIdAt(payOk(other, "50.00"), 0);

        voidPayment(id, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        voidPayment(id, otherPayment)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        voidPayment(UUID.randomUUID(), otherPayment)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        doGet(BASE + "/" + other).andExpect(jsonPath("$.payments[0].voidedAt").doesNotExist());
    }

    @Test
    void nothingCanBeVoidedOnACancelledOrderNotEvenTheRefund() throws Exception {
        UUID id = pendingOrder();
        doPost(BASE + "/" + id + "/confirm", confirmBody("50.00", "PIX", null)).andExpect(status().isOk());
        String cancelled = doPost(BASE + "/" + id + "/cancel", """
                {"refundMethod":"PIX"}""")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID payment = paymentIdAt(cancelled, 0);
        UUID refund = paymentIdAt(cancelled, 1);

        for (UUID target : List.of(payment, refund)) {
            voidPayment(id, target)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));
        }

        assertNetIsZero(detail(id));
    }

    @Test
    void cancellingAnOrderWithDepositsRefundsTheNetAndLeavesNothingReceived() throws Exception {
        UUID id = confirmedOrder();
        payOk(id, "30.00");
        payOk(id, "20.00");

        String cancelled = doPost(BASE + "/" + id + "/cancel", """
                {"refundMethod":"CASH"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.payments.length()").value(3))
                .andExpect(jsonPath("$.payments[2].type").value("REFUND"))
                .andExpect(jsonPath("$.payments[2].amount").value(50.0))
                .andExpect(jsonPath("$.payments[2].method").value("CASH"))
                .andExpect(jsonPath("$.payments[2].paidOn").value(today().toString()))
                .andExpect(jsonPath("$.payments[2].voidedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertNetIsZero(cancelled);
    }

    @Test
    void theRefundIgnoresVoidedPayments() throws Exception {
        UUID id = confirmedOrder();
        UUID typo = paymentIdAt(payOk(id, "40.00"), 0);
        voidPayment(id, typo).andExpect(status().isOk());
        payOk(id, "30.00");

        String cancelled = doPost(BASE + "/" + id + "/cancel", """
                {"refundMethod":"PIX"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(3))
                .andExpect(jsonPath("$.payments[2].type").value("REFUND"))
                .andExpect(jsonPath("$.payments[2].amount").value(30.0))
                .andReturn().getResponse().getContentAsString();

        assertNetIsZero(cancelled);
    }

    @Test
    void cancellingWithoutPaymentsCreatesNoRefundAndNeedsNoMethod() throws Exception {
        UUID withoutBody = confirmedOrder();
        doPost(BASE + "/" + withoutBody + "/cancel", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payments.length()").value(0));

        UUID withMethod = confirmedOrder();
        doPost(BASE + "/" + withMethod + "/cancel", """
                {"refundMethod":"PIX"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(0));
    }

    @Test
    void cancellingWithMoneyReceivedButNoRefundMethodIsRejectedAndChangesNothing() throws Exception {
        UUID id = confirmedOrder();
        payOk(id, "50.00");

        doPost(BASE + "/" + id + "/cancel", "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REFUND_METHOD_REQUIRED"));

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.payments.length()").value(1));
    }

    @Test
    void aRefundMethodOnAPendingOrderIsIgnoredAndAnInvalidOneIsMalformed() throws Exception {
        UUID pending = pendingOrder();
        doPost(BASE + "/" + pending + "/cancel", """
                {"refundMethod":"PIX"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payments.length()").value(0));

        UUID confirmed = confirmedOrder();
        doPost(BASE + "/" + confirmed + "/cancel", """
                {"refundMethod":"BITCOIN"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void aDeliveredOrderCannotBeCancelledAndItsPaymentsStayUntouched() throws Exception {
        UUID id = confirmedOrder();
        payOk(id, "50.00");
        doPost(BASE + "/" + id + "/deliver", "").andExpect(status().isOk());

        doPost(BASE + "/" + id + "/cancel", """
                {"refundMethod":"PIX"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.paidAmount").value(50.0));
    }

    @Test
    void theListShowsBalanceAndPaymentStatusOnlyForOrdersThatAcceptPayments() throws Exception {
        createScenarioOrders();

        doGet(BASE)
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.items[0].balance").value(150.0))
                .andExpect(jsonPath("$.items[1].paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.items[1].balance").value(100.0))
                .andExpect(jsonPath("$.items[2].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.items[2].balance").value(0.0))
                .andExpect(jsonPath("$.items[3].paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.items[3].balance").doesNotExist())
                .andExpect(jsonPath("$.items[4].paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.items[5].paymentStatus").value("PAID"));
    }

    @Test
    void thePaymentStatusFilterMatchesOnlyConfirmedOrDeliveredOrdersAndCountsCorrectly() throws Exception {
        UUID[] orders = createScenarioOrders();
        UUID unpaid = orders[0];
        UUID partial = orders[1];

        doGet(BASE + "?paymentStatus=UNPAID")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(unpaid.toString()));

        doGet(BASE + "?paymentStatus=PARTIAL")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(partial.toString()))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("PARTIAL"));

        doGet(BASE + "?paymentStatus=PAID")
                .andExpect(jsonPath("$.totalElements").value(2));

        doGet(BASE + "?paymentStatus=UNPAID&paymentStatus=PARTIAL")
                .andExpect(jsonPath("$.totalElements").value(2));

        doGet(BASE + "?paymentStatus=PAID&status=DELIVERED")
                .andExpect(jsonPath("$.totalElements").value(1));

        doGet(BASE + "?paymentStatus=PAID&size=1")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));

        doGet(BASE + "?paymentStatus=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void theSqlFilterAgreesWithTheCalculatorForEveryKindOfLedger() throws Exception {
        List<UUID> orders = new ArrayList<>();

        orders.add(confirmedOrder());                                   // no entries

        UUID oneCent = confirmedOrder();                                // smallest partial
        payOk(oneCent, "0.01");
        orders.add(oneCent);

        UUID oneCentShort = confirmedOrder();                           // one cent short of the total
        payOk(oneCentShort, "149.99");
        orders.add(oneCentShort);

        UUID exact = confirmedOrder();                                  // exactly the total
        payOk(exact, "150.00");
        orders.add(exact);

        UUID inParts = confirmedOrder();                                // total reached in two parts
        payOk(inParts, "100.00");
        payOk(inParts, "50.00");
        orders.add(inParts);

        UUID onlyVoided = confirmedOrder();                             // everything voided
        voidPayment(onlyVoided, paymentIdAt(payOk(onlyVoided, "80.00"), 0)).andExpect(status().isOk());
        orders.add(onlyVoided);

        UUID voidedAndReal = confirmedOrder();                          // a voided typo and a real payment
        voidPayment(voidedAndReal, paymentIdAt(payOk(voidedAndReal, "100.00"), 0)).andExpect(status().isOk());
        payOk(voidedAndReal, "20.00");
        orders.add(voidedAndReal);

        UUID delivered = confirmedOrder();                              // paid and delivered
        payOk(delivered, "150.00");
        doPost(BASE + "/" + delivered + "/deliver", "").andExpect(status().isOk());
        orders.add(delivered);

        for (String status : List.of("UNPAID", "PARTIAL", "PAID")) {
            Set<String> expected = new HashSet<>();
            for (UUID id : orders) {
                String reported = JsonPath.read(detail(id), "$.paymentStatus");
                if (status.equals(reported)) {
                    expected.add(id.toString());
                }
            }

            List<String> filtered = JsonPath.read(
                    doGet(BASE + "?paymentStatus=" + status + "&size=100")
                            .andReturn().getResponse().getContentAsString(),
                    "$.items[*].id");

            assertThat(expected).as("orders reported as " + status).isNotEmpty();
            assertThat(new HashSet<>(filtered)).as("SQL filter for " + status).isEqualTo(expected);
        }
    }

    @Test
    void twoSimultaneousPaymentsThatTogetherExceedTheBalanceCannotBothSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                UUID id = confirmedOrder();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);

                List<Future<MvcResult>> calls = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    calls.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return pay(id, "100.00").andReturn();
                    }));
                }
                ready.await();
                go.countDown();

                int created = 0;
                for (Future<MvcResult> call : calls) {
                    MvcResult result = call.get(30, TimeUnit.SECONDS);
                    int httpStatus = result.getResponse().getStatus();
                    if (httpStatus == 201) {
                        created++;
                        continue;
                    }
                    assertThat(httpStatus).isIn(409, 422);
                    String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
                    assertThat(code).isIn("CONCURRENT_MODIFICATION", "PAYMENT_EXCEEDS_BALANCE");
                }
                assertThat(created).isEqualTo(1);

                doGet(BASE + "/" + id)
                        .andExpect(jsonPath("$.payments.length()").value(1))
                        .andExpect(jsonPath("$.paidAmount").value(100.0))
                        .andExpect(jsonPath("$.balance").value(50.0));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID pendingOrder() throws Exception {
        return createOrder(ana, "2026-12-05", item(brigadeiro, 10));
    }

    private UUID confirmedOrder() throws Exception {
        UUID id = pendingOrder();
        doPost(BASE + "/" + id + "/confirm", "").andExpect(status().isOk());
        return id;
    }

    private UUID[] createScenarioOrders() throws Exception {
        UUID unpaid = confirmedOrder();

        UUID partial = confirmedOrder();
        payOk(partial, "50.00");

        UUID paid = confirmedOrder();
        payOk(paid, "150.00");

        UUID pending = pendingOrder();

        UUID cancelled = confirmedOrder();
        payOk(cancelled, "50.00");
        doPost(BASE + "/" + cancelled + "/cancel", """
                {"refundMethod":"CASH"}""").andExpect(status().isOk());

        UUID deliveredPaid = confirmedOrder();
        payOk(deliveredPaid, "150.00");
        doPost(BASE + "/" + deliveredPaid + "/deliver", "").andExpect(status().isOk());

        return new UUID[]{unpaid, partial, paid, pending, cancelled, deliveredPaid};
    }

    private ResultActions pay(UUID orderId, String amount) throws Exception {
        return pay(orderId, amount, null);
    }

    private ResultActions pay(UUID orderId, String amount, String paidOn) throws Exception {
        return doPost(BASE + "/" + orderId + "/payments", paymentJson(amount, "PIX", paidOn, null));
    }

    private String payOk(UUID orderId, String amount) throws Exception {
        return pay(orderId, amount)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions voidPayment(UUID orderId, UUID paymentId) throws Exception {
        return doPost(BASE + "/" + orderId + "/payments/" + paymentId + "/void", "");
    }

    private String detail(UUID orderId) throws Exception {
        return doGet(BASE + "/" + orderId).andReturn().getResponse().getContentAsString();
    }

    private static String paymentJson(String amount, String method, String paidOn, String notes) {
        return """
                {"amount":%s,"method":%s,"paidOn":%s,"notes":%s}"""
                .formatted(amount, quoted(method), quoted(paidOn), quoted(notes));
    }

    private static String confirmBody(String amount, String method, String paidOn) {
        return """
                {"initialPayment":{"amount":%s,"method":"%s","paidOn":%s,"notes":"Sinal"}}"""
                .formatted(amount, method, quoted(paidOn));
    }

    private static UUID paymentIdAt(String detailJson, int index) {
        return UUID.fromString(JsonPath.read(detailJson, "$.payments[" + index + "].id"));
    }

    @SuppressWarnings("unchecked")
    private static void assertNetIsZero(String detailJson) {
        List<Map<String, Object>> payments = JsonPath.read(detailJson, "$.payments");

        BigDecimal net = BigDecimal.ZERO;
        for (Map<String, Object> payment : payments) {
            if (payment.get("voidedAt") != null) {
                continue;
            }
            BigDecimal amount = new BigDecimal(payment.get("amount").toString());
            net = "PAYMENT".equals(payment.get("type")) ? net.add(amount) : net.subtract(amount);
        }

        assertThat(net).isEqualByComparingTo("0");
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private static LocalDate tomorrow() {
        return today().plusDays(1);
    }
}