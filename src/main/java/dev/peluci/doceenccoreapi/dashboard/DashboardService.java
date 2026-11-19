package dev.peluci.doceenccoreapi.dashboard;

import dev.peluci.doceenccoreapi.dashboard.DashboardRepository.DeliveryCounts;
import dev.peluci.doceenccoreapi.dashboard.DashboardRepository.ReceivableRow;
import dev.peluci.doceenccoreapi.dashboard.DashboardRepository.TodayRow;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse.Bucket;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse.Month;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse.Overdue;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse.Receivable;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse.Today;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse.TodayOrder;
import dev.peluci.doceenccoreapi.dashboard.dto.DashboardResponse.Week;
import dev.peluci.doceenccoreapi.order.OrderStatus;
import dev.peluci.doceenccoreapi.payment.PaymentService;
import dev.peluci.doceenccoreapi.payment.PaymentType;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Scale.MONEY);

    private final DashboardRepository repository;
    private final PaymentService payments;
    private final Clock clock;

    public DashboardService(DashboardRepository repository, PaymentService payments, Clock clock) {
        this.repository = repository;
        this.payments = payments;
        this.clock = clock;
    }

    public DashboardResponse get() {
        DashboardPeriods periods = DashboardPeriods.at(clock);

        List<TodayRow> rows = repository.findDeliveriesOn(OrderStatus.CONFIRMED, periods.today());
        Map<UUID, BigDecimal> nets = payments.netsOf(rows.stream().map(TodayRow::getId).toList());
        List<TodayOrder> todayOrders = rows.stream()
                .map(row -> new TodayOrder(
                        row.getId(),
                        row.getOrderNumber(),
                        row.getCustomerName(),
                        row.getFulfillmentType(),
                        row.getTotalAmount(),
                        row.getTotalAmount().subtract(nets.get(row.getId()))))
                .toList();

        DeliveryCounts counts = repository.countDeliveries(OrderStatus.CONFIRMED, periods.today(), periods.weekEnd());

        BigDecimal sold = repository
                .soldBetween(OrderStatus.DELIVERED, periods.monthStart(), periods.nextMonthStart())
                .orElse(ZERO);
        BigDecimal received = repository
                .receivedBetween(PaymentType.PAYMENT, periods.monthFrom(), periods.monthTo())
                .orElse(ZERO);

        return new DashboardResponse(
                new Today(periods.today(), todayOrders),
                new Week(periods.today(), periods.weekEnd(), counts.getWeekCount()),
                new Overdue(periods.yesterday(), counts.getOverdueCount()),
                new Month(periods.monthFrom(), periods.monthTo(), sold, received),
                receivable());
    }

    private Receivable receivable() {
        Map<String, ReceivableRow> byStatus = repository.receivable().stream()
                .collect(Collectors.toMap(ReceivableRow::getStatus, Function.identity()));

        Bucket dueOnDelivery = bucket(byStatus.get(OrderStatus.CONFIRMED.name()));
        Bucket unpaidDelivered = bucket(byStatus.get(OrderStatus.DELIVERED.name()));

        return new Receivable(
                dueOnDelivery.amount().add(unpaidDelivered.amount()),
                dueOnDelivery.ordersCount() + unpaidDelivered.ordersCount(),
                dueOnDelivery,
                unpaidDelivered);
    }

    private static Bucket bucket(ReceivableRow row) {
        return row == null ? new Bucket(ZERO, 0) : new Bucket(row.getAmount(), row.getOrdersCount());
    }
}
