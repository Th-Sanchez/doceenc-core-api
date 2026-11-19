package dev.peluci.doceenccoreapi.dashboard;

import dev.peluci.doceenccoreapi.order.Order;
import dev.peluci.doceenccoreapi.order.OrderApiTestSupport;
import dev.peluci.doceenccoreapi.order.OrderRepository;
import dev.peluci.doceenccoreapi.payment.OrderPayment;
import dev.peluci.doceenccoreapi.payment.OrderPaymentRepository;
import dev.peluci.doceenccoreapi.payment.PaymentMethod;
import dev.peluci.doceenccoreapi.payment.PaymentService;
import dev.peluci.doceenccoreapi.payment.PaymentType;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.MutableClock;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DashboardIntegrationTest extends OrderApiTestSupport {

    private static final String URL = "/api/v1/dashboard";
    private static final String WEDNESDAY = "2026-12-02T15:00:00Z";

    @Autowired private MutableClock clock;
    @Autowired private OrderRepository orders;
    @Autowired private OrderPaymentRepository payments;
    @Autowired private PaymentService paymentService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void resetClock() {
        setClock(WEDNESDAY);
    }

    @AfterEach
    void restoreClock() {
        clock.reset();
    }

    private void setClock(String isoInstant) {
        clock.setInstant(Instant.parse(isoInstant));
    }

    @Test
    void deliveriesAreSplitIntoTodayWeekAndOverdue() throws Exception {
        UUID today = confirmed(ana, "2026-12-02", 10);
        confirmed(ana, "2026-12-03", 10);
        confirmed(ana, "2026-12-06", 10);
        confirmed(ana, "2026-12-07", 10);
        confirmed(ana, "2026-12-01", 10);

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.today.date").value("2026-12-02"))
                .andExpect(jsonPath("$.today.orders.length()").value(1))
                .andExpect(jsonPath("$.today.orders[0].id").value(today.toString()))
                .andExpect(jsonPath("$.today.orders[0].customerName").value("Ana"))
                .andExpect(jsonPath("$.today.orders[0].fulfillmentType").value("PICKUP"))
                .andExpect(jsonPath("$.today.orders[0].totalAmount").value(150.0))
                .andExpect(jsonPath("$.thisWeek.from").value("2026-12-02"))
                .andExpect(jsonPath("$.thisWeek.to").value("2026-12-06"))
                .andExpect(jsonPath("$.thisWeek.ordersCount").value(3))
                .andExpect(jsonPath("$.overdueCount").value(1));
    }

    @Test
    void pendingAndCancelledOrdersAreIgnored() throws Exception {
        createOrder(ana, "2026-12-02", item(brigadeiro, 10));
        UUID cancelled = confirmed(ana, "2026-12-02", 10);
        cancel(cancelled);

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.today.orders", empty()))
                .andExpect(jsonPath("$.thisWeek.ordersCount").value(0))
                .andExpect(jsonPath("$.overdueCount").value(0));
    }

    @Test
    void todayOrdersCarryTheirBalanceAndAreOrderedByNumber() throws Exception {
        UUID first = confirmed(ana, "2026-12-02", 10);
        UUID second = confirmed(ana, "2026-12-02", 10);
        pay(first, "50.00", "2026-12-02");

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.today.orders[*].id", contains(first.toString(), second.toString())))
                .andExpect(jsonPath("$.today.orders[0].balance").value(100.0))
                .andExpect(jsonPath("$.today.orders[1].balance").value(150.0));
    }

    @Test
    void lastDayOfMonthAtNightBelongsToThatMonthInLocalTime() throws Exception {
        UUID id = confirmed(ana, "2026-12-31", 10);
        setClock("2027-01-01T02:30:00Z");
        deliver(id);

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.month.from").value("2026-12-01"))
                .andExpect(jsonPath("$.month.to").value("2026-12-31"))
                .andExpect(jsonPath("$.month.soldAmount").value(150.0));

        setClock("2027-01-01T03:00:00Z");
        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.month.from").value("2027-01-01"))
                .andExpect(jsonPath("$.month.soldAmount").value(0.0));
    }

    @Test
    void receivedFollowsPaidOnAndSoldFollowsDeliveryMonth() throws Exception {
        setClock("2026-11-28T15:00:00Z");
        UUID a = confirmed(ana, "2026-11-28", 10);
        deliver(a);
        setClock(WEDNESDAY);
        pay(a, "150.00", "2026-12-01");

        setClock("2026-11-30T15:00:00Z");
        UUID b = confirmed(ana, "2026-12-02", 10);
        pay(b, "150.00", "2026-11-30");
        setClock(WEDNESDAY);
        deliver(b);

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.month.soldAmount").value(150.0))
                .andExpect(jsonPath("$.month.receivedAmount").value(150.0));
    }

    @Test
    void voidedPaymentsAreIgnoredAndRefundsSubtract() throws Exception {
        UUID kept = confirmed(ana, "2026-12-03", 10);
        pay(kept, "50.00", "2026-12-02");

        UUID voided = confirmed(ana, "2026-12-03", 10);
        pay(voided, "100.00", "2026-12-02");
        voidFirstPayment(voided);

        UUID refunded = confirmed(ana, "2026-12-03", 10);
        pay(refunded, "150.00", "2026-12-02");
        cancel(refunded);

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.month.receivedAmount").value(50.0));
    }

    @Test
    void receivedCanBeNegativeWhenARefundReturnsLastMonthsMoney() throws Exception {
        setClock("2026-11-20T15:00:00Z");
        UUID id = confirmed(ana, "2026-12-10", 10);
        pay(id, "100.00", "2026-11-20");
        setClock(WEDNESDAY);
        cancel(id);

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.month.receivedAmount").value(-100.0));
    }

    @Test
    void monthWithoutDataReturnsZeros() throws Exception {
        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.today.orders", empty()))
                .andExpect(jsonPath("$.thisWeek.ordersCount").value(0))
                .andExpect(jsonPath("$.overdueCount").value(0))
                .andExpect(jsonPath("$.month.soldAmount").value(0.0))
                .andExpect(jsonPath("$.month.receivedAmount").value(0.0))
                .andExpect(jsonPath("$.receivable.amount").value(0.0))
                .andExpect(jsonPath("$.receivable.ordersCount").value(0))
                .andExpect(jsonPath("$.receivable.dueOnDelivery.amount").value(0.0))
                .andExpect(jsonPath("$.receivable.unpaidDelivered.ordersCount").value(0));
    }

    @Test
    void receivableSeparatesConfirmedFromDeliveredAndSkipsSettledAndCancelled() throws Exception {
        seedReceivables();

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivable.amount").value(215.4))
                .andExpect(jsonPath("$.receivable.ordersCount").value(2))
                .andExpect(jsonPath("$.receivable.dueOnDelivery.amount").value(100.0))
                .andExpect(jsonPath("$.receivable.dueOnDelivery.ordersCount").value(1))
                .andExpect(jsonPath("$.receivable.unpaidDelivered.amount").value(115.4))
                .andExpect(jsonPath("$.receivable.unpaidDelivered.ordersCount").value(1));
    }

    @Test
    void receivableAgreesWithTheLedgerNetsUsedByTheOrderBalance() throws Exception {
        seedReceivables();

        List<Order> open = orders.findAll().stream()
                .filter(o -> Order.PAYABLE_STATUSES.contains(o.getStatus()))
                .toList();
        Map<UUID, BigDecimal> nets = paymentService.netsOf(open.stream().map(Order::getId).toList());
        BigDecimal expected = open.stream()
                .map(o -> o.getTotalAmount().subtract(nets.get(o.getId())))
                .filter(balance -> balance.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        doGet(URL).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivable.amount").value(expected.doubleValue()));
    }

    @Test
    void numberOfQueriesDoesNotGrowWithTheOrdersShownToday() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        confirmed(ana, "2026-12-02", 10);
        long withOne = queriesOf(statistics);

        for (int i = 0; i < 9; i++) {
            confirmed(ana, "2026-12-02", 10);
        }
        long withTen = queriesOf(statistics);

        assertThat(withTen).isEqualTo(withOne);
        assertThat(withTen).isLessThanOrEqualTo(7);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }


    private long queriesOf(Statistics statistics) throws Exception {
        statistics.clear();
        doGet(URL).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private void seedReceivables() throws Exception {
        UUID partial = confirmed(ana, "2026-12-03", 10);
        pay(partial, "50.00", "2026-12-02");

        UUID settled = confirmed(ana, "2026-12-03", 10);
        pay(settled, "150.00", "2026-12-02");

        UUID cancelled = confirmed(ana, "2026-12-03", 10);
        cancel(cancelled);

        UUID wholesale = createOrder(mercadinho, "2026-12-02", item(brigadeiro, 10));
        confirm(wholesale);
        deliver(wholesale);

        createOrder(ana, "2026-12-03", item(brigadeiro, 10));
    }

    private UUID confirmed(UUID customerId, String deliveryDate, int quantity) throws Exception {
        UUID id = createOrder(customerId, deliveryDate, item(brigadeiro, quantity));
        confirm(id);
        return id;
    }

    private void confirm(UUID id) throws Exception {
        doPost(BASE + "/" + id + "/confirm", "{}").andExpect(status().isOk());
    }

    private void deliver(UUID id) throws Exception {
        doPost(BASE + "/" + id + "/deliver", "").andExpect(status().isOk());
    }

    private void cancel(UUID id) throws Exception {
        doPost(BASE + "/" + id + "/cancel", "{\"refundMethod\":\"PIX\"}").andExpect(status().isOk());
    }

    private void pay(UUID orderId, String amount, String paidOn) {
        payments.save(new OrderPayment(orderId, PaymentType.PAYMENT, new BigDecimal(amount),
                PaymentMethod.PIX, LocalDate.parse(paidOn), null));
    }

    private void voidFirstPayment(UUID orderId) {
        UUID paymentId = payments.findByOrderIdOrderByPaidOnAscCreatedAtAsc(orderId).getFirst().getId();
        paymentService.voidPayment(orderId, paymentId);
    }
}