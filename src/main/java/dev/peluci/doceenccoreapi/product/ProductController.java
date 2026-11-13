package dev.peluci.doceenccoreapi.product;

import dev.peluci.doceenccoreapi.product.dto.CreateProductRequest;
import dev.peluci.doceenccoreapi.product.dto.ProductDetailResponse;
import dev.peluci.doceenccoreapi.product.dto.ProductSimulationResponse;
import dev.peluci.doceenccoreapi.product.dto.ProductSummaryResponse;
import dev.peluci.doceenccoreapi.product.dto.SimulateProductRequest;
import dev.peluci.doceenccoreapi.product.dto.UpdateProductRequest;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/produts")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductSummaryResponse> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<ProductDetailResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductDetailResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ProductDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    public ProductDetailResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/restore")
    public ProductDetailResponse restore(@PathVariable UUID id) {
        return service.restore(id);
    }

    @PostMapping("/simulation")
    public ProductSimulationResponse simulate(@Valid @RequestBody SimulateProductRequest request) {
        return service.simulate(request);
    }
}
