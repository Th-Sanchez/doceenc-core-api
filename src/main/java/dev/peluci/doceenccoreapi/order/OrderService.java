package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.customer.CustomerRepository;
import dev.peluci.doceenccoreapi.order.Order.ItemDraft;
import dev.peluci.doceenccoreapi.order.PricedOrder.PricedLine;
import dev.peluci.doceenccoreapi.order.dto.OrderDetailResponse;
import dev.peluci.doceenccoreapi.order.dto.OrderItemRequest;
import dev.peluci.doceenccoreapi.order.dto.OrderRequest;
import dev.peluci.doceenccoreapi.product.Product;
import dev.peluci.doceenccoreapi.product.ProductRepository;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final AuditingHandler auditingHandler;
    private final Clock clock;

    public OrderService(OrderRepository orders, CustomerRepository customers, ProductRepository products, AuditingHandler auditingHandler, Clock clock) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.auditingHandler = auditingHandler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse get(UUID id) {
        return detail(load(id));
    }

    public OrderDetailResponse create(OrderRequest request) {
        Customer customer = loadActiveCustomer(request.customerId());
        List<ItemDraft> drafts = resolveDrafts(request.items());

        Order order = new Order(customer, request.fulfillmentType(), blankToNull(request.deliveryAddress()),
                request.deliveryDate(), blankToNull(request.notes()));
        order.replaceItems(drafts);

        assertLinesUsable(PricedOrder.of(order));
        return detail(orders.saveAndFlush(order));
    }

    public OrderDetailResponse update(UUID id, OrderRequest request) {
        Order order = load(id);
        order.assertEditable();

        Customer customer = loadActiveCustomer(request.customerId());
        List<ItemDraft> drafts = resolveDrafts(request.items());

        order.update(customer, request.fulfillmentType(), blankToNull(request.deliveryAddress()),
                request.deliveryDate(), blankToNull(request.notes()));
        boolean itemsChanged = order.replaceItems(drafts);

        assertLinesUsable(PricedOrder.of(order));
        if (itemsChanged) {
            auditingHandler.markModified(order);
        }
        return detail(orders.saveAndFlush(order));
    }

    public OrderDetailResponse confirm(UUID id) {
        Order order = load(id);
        order.assertCanConfirm();
        requireActive(order.getCustomer());

        PricedOrder priced = PricedOrder.of(order);
        assertLinesUsable(priced);

        order.confirm(clock.instant(), priced.channel(), priced.calculation());
        return detail(orders.saveAndFlush(order));
    }

    public OrderDetailResponse deliver(UUID id) {
        Order order = load(id);
        order.deliver(clock.instant());
        return detail(orders.saveAndFlush(order));
    }

    public OrderDetailResponse cancel(UUID id) {
        Order order = load(id);
        order.cancel(clock.instant());
        return detail(orders.saveAndFlush(order));
    }

    private OrderDetailResponse detail(Order order) {
        return OrderDetailResponse.from(PricedOrder.of(order));
    }

    private Order load(UUID id) {
        return orders.findWithItemsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND,
                        "Order not found."));
    }

    private Customer loadActiveCustomer(UUID id) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found."));
        requireActive(customer);
        return customer;
    }

    private static void requireActive(Customer customer) {
        if (!customer.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ARCHIVED_CUSTOMER_NOT_ALLOWED,
                    "Customer " + customer.getId() + " is archived and cannot have orders.");
        }
    }

    private List<ItemDraft> resolveDrafts(List<OrderItemRequest> items) {
        Set<UUID> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!seen.add(item.productId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.DUPLICATE_ORDER_ITEM,
                        "Product " + item.productId() + " appears more than once.");
            }
        }

        Map<UUID, Product> byId = products.findAllByIdIn(seen).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return items.stream().map(item -> {
            Product product = byId.get(item.productId());
            if (product == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PRODUCT_NOT_FOUND,
                        "Product " + item.productId() + " not found.");
            }
            return new ItemDraft(product, item.quantity());
        }).toList();
    }

    private static void assertLinesUsable(PricedOrder priced) {
        for (PricedLine line : priced.lines()) {
            UUID productId = line.item().getProduct().getId();
            if (line.issue() == LineIssue.ARCHIVED) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ARCHIVED_PRODUCT_NOT_ALLOWED,
                        "Product " + productId + " is archived and cannot be in an order.");
            }
            if (line.issue() == LineIssue.NO_PRICE) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PRODUCT_PRICE_UNAVAILABLE,
                        "Product " + productId + " has no " + priced.channel() + " price.");
            }
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

}
