package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.customer.Customer;
import dev.peluci.doceenccoreapi.customer.CustomerRepository;
import dev.peluci.doceenccoreapi.order.Order.ItemDraft;
import dev.peluci.doceenccoreapi.order.PricedOrder.PricedLine;
import dev.peluci.doceenccoreapi.order.dto.OrderDetailResponse;
import dev.peluci.doceenccoreapi.order.dto.OrderItemRequest;
import dev.peluci.doceenccoreapi.order.dto.OrderRequest;
import dev.peluci.doceenccoreapi.order.dto.OrderSimulationResponse;
import dev.peluci.doceenccoreapi.order.dto.OrderSimulationResquest;
import dev.peluci.doceenccoreapi.order.dto.OrderSummaryResponse;
import dev.peluci.doceenccoreapi.payment.PaymentMethod;
import dev.peluci.doceenccoreapi.payment.PaymentService;
import dev.peluci.doceenccoreapi.payment.dto.PaymentRequest;
import dev.peluci.doceenccoreapi.product.Product;
import dev.peluci.doceenccoreapi.product.ProductRepository;
import dev.peluci.doceenccoreapi.shared.PageResponse;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
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

    public static final int MAX_PAGE_SIZE = 100;

    private static final Sort LIST_ORDER = Sort.by(Sort.Order.asc("deliveryDate"), Sort.Order.asc("orderNumber"));

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final AuditingHandler auditingHandler;
    private final Clock clock;
    private final OrderDetailAssembler assembler;
    private final PaymentService payments;

    public OrderService(OrderRepository orders, CustomerRepository customers, ProductRepository products, AuditingHandler auditingHandler, Clock clock, OrderDetailAssembler assembler, PaymentService payments) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.auditingHandler = auditingHandler;
        this.clock = clock;
        this.assembler = assembler;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(OrderFilter filter, int page, int size) {
        if (page < 0) {
            throw invalidQuery("page must not be negative.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidQuery("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        if (filter.deliveryFrom() != null && filter.deliveryTo() != null
                && filter.deliveryFrom().isAfter(filter.deliveryTo())) {
            throw invalidQuery("deliveryFrom must not be after deliveryTo.");
        }

        Page<Order> found = orders.findAll(OrderSpecifications.matching(filter),
                PageRequest.of(page, size, LIST_ORDER));
        return PageResponse.from(found, order ->
                OrderSummaryResponse.from(order, PricedOrder.totalOf(order)));
    }

    @Transactional(readOnly = true)
    public OrderSimulationResponse simulate(OrderSimulationResquest request) {
        PriceChannel channel = PricedOrder.channelOf(loadActiveCustomer(request.customerId()));
        List<OrderItemRequest> items = request.items();

        Map<UUID, Product> byId = loadProducts(items.stream()
                .map(OrderItemRequest::productId).collect(Collectors.toSet()));

        LineIssue[] issues = new LineIssue[items.size()];
        List<Integer> priceable = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            UUID productId = items.get(i).productId();
            Product product = byId.get(productId);

            if(!seen.add(productId)) {
                issues[i] = LineIssue.DUPLICATE;
            } else if (product == null) {
                issues[i] = LineIssue.NOT_FOUND;
            } else if (!product.isActive()) {
                issues[i] = LineIssue.ARCHIVED;
            } else {
                priceable.add(i);
            }
        }

        OrderPricingCalculator.Result result = OrderPricingCalculator.calculate(channel, priceable.stream()
                .map(i -> PricedOrder.itemFor(byId.get(items.get(i).productId()), items.get(i).quantity()))
                .toList());

        List<OrderSimulationResponse.ItemResponse> lines = new ArrayList<>();
        int nextPriced = 0;

        for (int i = 0; i < items.size(); i++) {
            OrderItemRequest item = items.get(i);
            Product product = byId.get(item.productId());
            String name = product != null ? product.getName() : null;

            if (issues[i] != null) {
                lines.add(new OrderSimulationResponse.ItemResponse(item.productId(), name, item.quantity(),
                        null, null, null, issues[i]));
                continue;
            }

            OrderPricingCalculator.Line line = result.lines().get(nextPriced++);
            boolean priced = line.status() == OrderPricingCalculator.LineStatus.PRICED;
            lines.add(new OrderSimulationResponse.ItemResponse(item.productId(), name, item.quantity(),
                    line.unitPrice(), line.unitCost(), line.subtotal(), priced ? null : LineIssue.NO_PRICE));
        }

        BigDecimal total = result.total();
        return new OrderSimulationResponse(channel, lines, total);
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
        return detail(save(order));
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
        return detail(save(order));
    }

    public OrderDetailResponse confirm(UUID id, PaymentRequest initialPayment) {
        Order order = load(id);
        order.assertCanConfirm();
        requireActive(order.getCustomer());

        PricedOrder priced = PricedOrder.of(order);
        assertLinesUsable(priced);

        order.confirm(clock.instant(), priced.channel(), priced.calculation());

        if (initialPayment != null) {
            payments.register(order.getId(), order.getTotalAmount(), initialPayment);
        }

        return detail(save(order));
    }

    public OrderDetailResponse deliver(UUID id) {
        Order order = load(id);
        order.deliver(clock.instant());
        return detail(save(order));
    }

    public OrderDetailResponse cancel(UUID id, PaymentMethod refundMethod) {
        Order order = load(id);
        boolean wasConfirmed = order.getStatus() == OrderStatus.CONFIRMED;

        order.cancel(clock.instant());

        if (wasConfirmed) {
            payments.refundNet(order.getId(), refundMethod);
        }

        return detail(save(order));
    }

    private Order save(Order order) {
        try {
            return orders.saveAndFlush(order);
        } catch (OptimisticLockingFailureException ex) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                    "The order was changed by another request. Reload it and try again.");
        }
    }

    private static ApiException invalidQuery(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    private OrderDetailResponse detail(Order order) {
        return assembler.assemble(order);
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

    private Map<UUID, Product> loadProducts(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        return products.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
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

        Map<UUID, Product> byId = loadProducts(seen);

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
