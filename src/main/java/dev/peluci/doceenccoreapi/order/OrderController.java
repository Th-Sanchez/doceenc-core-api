package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.order.dto.CancelRequest;
import dev.peluci.doceenccoreapi.order.dto.ConfirmRequest;
import dev.peluci.doceenccoreapi.order.dto.OrderDetailResponse;
import dev.peluci.doceenccoreapi.order.dto.OrderRequest;
import dev.peluci.doceenccoreapi.order.dto.OrderSimulationResponse;
import dev.peluci.doceenccoreapi.order.dto.OrderSimulationResquest;
import dev.peluci.doceenccoreapi.order.dto.OrderSummaryResponse;
import dev.peluci.doceenccoreapi.payment.dto.PaymentRequest;
import dev.peluci.doceenccoreapi.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final String BASE = "/api/v1/orders";

    private final OrderService service;
    private final OrderPaymentService orderPayments;

    public OrderController(OrderService service, OrderPaymentService orderPayments) {
        this.service = service;
        this.orderPayments = orderPayments;
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryTo,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(new OrderFilter(status, deliveryFrom, deliveryTo, customerId), page, size);
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/simulation")
    public OrderSimulationResponse simulate(@Valid @RequestBody OrderSimulationResquest request) {
        return service.simulate(request);
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(@Valid @RequestBody OrderRequest request) {
        OrderDetailResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public OrderDetailResponse update(@PathVariable UUID id, @Valid @RequestBody OrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/confirm")
    public OrderDetailResponse confirm(@PathVariable UUID id, @Valid @RequestBody(required = false) ConfirmRequest request) {
        return service.confirm(id, request == null ? null : request.initialPayment());
    }

    @PostMapping("/{id}/deliver")
    public OrderDetailResponse deliver(@PathVariable UUID id) {
        return service.deliver(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDetailResponse cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
        return service.cancel(id, request == null ? null : request.refundMethod());
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<OrderDetailResponse> registerPayment(@PathVariable UUID id,
                                                               @Valid @RequestBody PaymentRequest request) {
        OrderDetailResponse detail = orderPayments.register(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path(BASE + "/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(detail);
    }

    @PostMapping("/{id}/payments/{paymentId}/void")
    public OrderDetailResponse voidPayment(@PathVariable UUID id, @PathVariable UUID paymentId) {
        return orderPayments.voidPayment(id, paymentId);
    }
}
