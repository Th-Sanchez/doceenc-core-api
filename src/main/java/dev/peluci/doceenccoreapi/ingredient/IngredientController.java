package dev.peluci.doceenccoreapi.ingredient;

import dev.peluci.doceenccoreapi.ingredient.dto.CreateIngredientRequest;
import dev.peluci.doceenccoreapi.ingredient.dto.IngredientResponse;
import dev.peluci.doceenccoreapi.ingredient.dto.PriceHistoryResponse;
import dev.peluci.doceenccoreapi.ingredient.dto.PriceRequest;
import dev.peluci.doceenccoreapi.ingredient.dto.UpdateIngredientRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientController {

    private final IngredientService service;

    public IngredientController(IngredientService service) {
        this.service = service;
    }

    @GetMapping
    public List<IngredientResponse> list(@RequestParam(required = false) UUID categoryId,
                                         @RequestParam(required = false) Boolean active,
                                         @RequestParam(name = "q", required = false) String text) {
        return service.list(categoryId, active, text);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse create(@Valid @RequestBody CreateIngredientRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public IngredientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateIngredientRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse registerPurchase(@PathVariable UUID id, @Valid @RequestBody PriceRequest request) {
        return service.registerPurchase(id, request);
    }

    @GetMapping("/{id}/prices")
    public List<PriceHistoryResponse> priceHistory(@PathVariable UUID id) {
        return service.priceHistory(id);
    }

    @PostMapping("/{id}/archive")
    public IngredientResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/restore")
    public IngredientResponse restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}
