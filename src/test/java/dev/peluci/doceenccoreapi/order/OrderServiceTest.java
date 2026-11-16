package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.customer.CustomerRepository;
import dev.peluci.doceenccoreapi.customer.CustomerType;
import dev.peluci.doceenccoreapi.product.ProductRepository;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);

    private final OrderService service = new OrderService(orders, mock(CustomerRepository.class),
            mock(ProductRepository.class), mock(AuditingHandler.class),
            Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void aVersionConflictOnSaveBecomesConcurrentModification() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer("Ana", CustomerType.RETAIL, null, null, null, null);
        Order order = new Order(customer, FulfillmentType.PICKUP, null, null, null);
        when(orders.findWithItemsById(id)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenThrow(new ObjectOptimisticLockingFailureException(Order.class, id));

        assertThatThrownBy(() -> service.cancel(id))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
                });
    }
}