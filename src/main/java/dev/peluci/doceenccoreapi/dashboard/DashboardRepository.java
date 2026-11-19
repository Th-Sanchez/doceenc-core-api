package dev.peluci.doceenccoreapi.dashboard;

import dev.peluci.doceenccoreapi.order.FulfillmentType;
import dev.peluci.doceenccoreapi.order.Order;
import dev.peluci.doceenccoreapi.order.OrderStatus;
import dev.peluci.doceenccoreapi.payment.PaymentType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardRepository extends Repository<Order, UUID> {

    interface TodayRow {
        UUID getId();
        Long getOrderNumber();
        String getCustomerName();
        FulfillmentType getFulfillmentType();
        BigDecimal getTotalAmount();
    }

    interface DeliveryCounts {
        long getOverdueCount();
        long getWeekCount();
    }

    interface ReceivableRow {
        String getStatus();
        long getOrdersCount();
        BigDecimal getAmount();
    }

    @Query("""
            SELECT o.id AS id,
                   o.orderNumber AS orderNumber,
                   c.name AS customerName,
                   o.fulfillmentType AS fulfillmentType,
                   o.totalAmount AS totalAmount
            FROM Order o JOIN o.customer c
            WHERE o.status = :status AND o.deliveryDate = :date
            ORDER BY o.orderNumber""")
    List<TodayRow> findDeliveriesOn(@Param("status") OrderStatus status, @Param("date") LocalDate date);

    @Query("""
            SELECT COUNT(CASE WHEN o.deliveryDate < :today THEN 1 END) AS overdueCount,
                   COUNT(CASE WHEN o.deliveryDate >= :today THEN 1 END) AS weekCount
            FROM Order o
            WHERE o.status = :status AND o.deliveryDate <= :weekEnd""")
    DeliveryCounts countDeliveries(@Param("status") OrderStatus status, @Param("today") LocalDate today, @Param("weekEnd") LocalDate weekEnd);

    @Query("""
            SELECT SUM (o.totalAmount)
            FROM Order o
            WHERE o.status = :status AND o.deliveredAt >= :start AND o.deliveredAt < :end""")
    Optional<BigDecimal> soldBetween(@Param("status") OrderStatus status, @Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            SELECT SUM (CASE WHEN p.type = :payment THEN p.amount ELSE -p.amount END)
            FROM OrderPayment p
            WHERE p.voidedAt IS NULL AND p.paidOn >= :from AND p.paidOn <= :to""")
    Optional<BigDecimal> receivedBetween(@Param("payment") PaymentType payment, @Param("from") LocalDate from, @Param("to") LocalDate to);


    @Query(value = """
            SELECT o.status AS "status",
                   COUNT(*) AS "ordersCount",
                   SUM(o.total_amount - COALESCE(n.net, 0)) AS "amount"
            FROM orders o
            LEFT JOIN (
                SELECT p.order_id,
                       SUM(CASE WHEN p.type = :payment THEN p.amount ELSE -p.amount END) AS net
                FROM order_payment p
                WHERE p.voided_at IS NULL
                GROUP BY p.order_id
            ) n ON n.order_id = o.id
            WHERE o.status IN (:statuses)
              AND o.total_amount > COALESCE(n.net, 0)
            GROUP BY o.status""", nativeQuery = true)
    List<ReceivableRow> receivableByStatus(@Param("payment") String payment, @Param("statuses") Collection<String> statuses);

    default List<ReceivableRow> receivable() {
        return receivableByStatus(PaymentType.PAYMENT.name(),
                Order.PAYABLE_STATUSES.stream().map(Enum::name).toList());
    }

}
