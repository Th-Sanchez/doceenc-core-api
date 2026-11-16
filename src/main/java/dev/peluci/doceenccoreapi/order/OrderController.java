package dev.peluci.doceenccoreapi.order;

import dev.peluci.doceenccoreapi.order.dto.OrderDetailResponse;
import dev.peluci.doceenccoreapi.order.dto.OrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
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

    @PutMapping("/{id}/confirm")
    public OrderDetailResponse confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @PutMapping("/{id}/deliver")
    public OrderDetailResponse deliver(@PathVariable UUID id) {
        return service.deliver(id);
    }

    @PutMapping("/{id}/cancel")
    public OrderDetailResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
