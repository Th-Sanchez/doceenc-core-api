package dev.peluci.doceenccoreapi.recipe;

import dev.peluci.doceenccoreapi.recipe.dto.CreateRecipeRequest;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeDetailResponse;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeSimulationResponse;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeSummaryResponse;
import dev.peluci.doceenccoreapi.recipe.dto.SimulateRecipeRequest;
import dev.peluci.doceenccoreapi.recipe.dto.UpdateRecipeRequest;
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
@RequestMapping("/api/v1/recipes")
public class RecipeController {

    private final RecipeService service;

    public RecipeController(RecipeService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecipeSummaryResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RecipeDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<RecipeDetailResponse> create(@Valid @RequestBody CreateRecipeRequest request) {
        RecipeDetailResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public RecipeDetailResponse update (@PathVariable UUID id, @Valid @RequestBody UpdateRecipeRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    public RecipeDetailResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/restore")
    public RecipeDetailResponse restore(@PathVariable UUID id) {
        return service.restore(id);
    }

    @PostMapping("/simulation")
    public RecipeSimulationResponse simulate(@Valid @RequestBody SimulateRecipeRequest request) {
        return service.simulate(request);
    }
}
