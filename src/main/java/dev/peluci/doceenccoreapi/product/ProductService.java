package dev.peluci.doceenccoreapi.product;

import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.ingredient.IngredientRepository;
import dev.peluci.doceenccoreapi.product.Product.ItemDraft;
import dev.peluci.doceenccoreapi.product.ProductPricingCalculator.UnitCost;
import dev.peluci.doceenccoreapi.product.dto.CreateProductRequest;
import dev.peluci.doceenccoreapi.product.dto.ProductDetailResponse;
import dev.peluci.doceenccoreapi.product.dto.ProductItemRequest;
import dev.peluci.doceenccoreapi.product.dto.ProductItemResponse;
import dev.peluci.doceenccoreapi.product.dto.ProductSimulationResponse;
import dev.peluci.doceenccoreapi.product.dto.ProductSummaryResponse;
import dev.peluci.doceenccoreapi.product.dto.SimulateProductRequest;
import dev.peluci.doceenccoreapi.product.dto.UpdateProductRequest;
import dev.peluci.doceenccoreapi.recipe.Recipe;
import dev.peluci.doceenccoreapi.recipe.RecipeRepository;
import dev.peluci.doceenccoreapi.settings.SettingsService;
import dev.peluci.doceenccoreapi.settings.TargetMargins;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final RecipeRepository recipes;
    private final IngredientRepository ingredients;
    private final SettingsService settings;
    private final AuditingHandler auditingHandler;

    public ProductService(ProductRepository products, RecipeRepository recipes, IngredientRepository ingredients, SettingsService settings, AuditingHandler auditingHandler) {
        this.products = products;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.settings = settings;
        this.auditingHandler = auditingHandler;
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> list() {
        TargetMargins margins = settings.targetMargins();
        return products.findAllWithRecipe().stream()
                .map(product -> ProductSummaryResponse.from(product, margins))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse get(UUID id) {
        return detail(load(id));
    }

    public ProductDetailResponse create(CreateProductRequest request) {
        String name = request.name().strip();
        assertNameIsFree(name, null);
        Recipe recipe = resolveRecipe(request.recipeId(), null);
        List<ItemDraft> drafts = resolveDrafts(request.items(), Set.of());

        Product product = new Product(name, recipe, request.recipeQuantity(),
                request.retailPrice(), request.wholesalePrice());
        product.replaceItems(drafts);
        return detail(products.saveAndFlush(product));
    }

    public ProductDetailResponse update(UUID id, UpdateProductRequest request) {
        Product product = load(id);
        if (!product.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PRODUCT_ARCHIVED,
                    "An archived product cannot be changed. Restore it first.");
        }

        String name = request.name().strip();
        assertNameIsFree(name, id);
        Recipe recipe = resolveRecipe(request.recipeId(), product.getRecipe().getId());

        Set<UUID> currentIngredientIds = product.getItems().stream()
                .map(item -> item.getIngredient().getId())
                .collect(Collectors.toSet());
        List<ItemDraft> drafts = resolveDrafts(request.items(), currentIngredientIds);

        product.update(name, recipe, request.recipeQuantity(), request.retailPrice(), request.wholesalePrice());
        if (product.replaceItems(drafts)) {
            auditingHandler.markModified(product);
        }
        return detail(products.saveAndFlush(product));
    }

    public ProductDetailResponse archive(UUID id) {
        Product product = load(id);
        product.archive();
        return detail(products.saveAndFlush(product));
    }

    public ProductDetailResponse restore(UUID id) {
        Product product = load(id);
        product.restore();
        return detail(products.saveAndFlush(product));
    }

    @Transactional
    public ProductSimulationResponse simulate(SimulateProductRequest request) {
        Recipe recipe = resolveRecipe(request.recipeId(), null);
        assertNoDuplicates(request.items());
        Map<UUID, Ingredient> byId = loadIngredients(request.items());

        List<ProductItemResponse> items = request.items().stream()
                .map(item -> {
                    Ingredient ingredient = byId.get(item.ingredientId());
                    assertIngredientUsable(ingredient, Set.of());
                    return ProductItemResponse.of(ingredient, item.quantity());
                })
                .sorted(ProductItemResponse.byIngredientName())
                .toList();

        UnitCost cost = ProductPricingCalculator.unitCost(
                recipe.cost().totalCost(),
                recipe.getYieldQuantity(),
                Scale.quantity(request.recipeQuantity()),
                items.stream().map(ProductItemResponse::cost).toList());

        return ProductSimulationResponse.of(recipe, items, cost,
                request.retailPrice(), request.wholesalePrice(), settings.targetMargins());
    }

    private ProductDetailResponse detail(Product product) {
        return ProductDetailResponse.from(product, settings.targetMargins());
    }

    private Product load(UUID id) {
        return products.findWithItemsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND,
                        "Product not found."));
    }

    private Recipe resolveRecipe(UUID recipeId, UUID currentRecipeId) {
        Recipe recipe = recipes.findWithItemsById(recipeId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.RECIPE_NOT_FOUND,
                        "Recipe not found."));

        if (!recipe.isActive() && !recipe.getId().equals(currentRecipeId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ARCHIVED_RECIPE_NOT_ALLOWED,
                    "Recipe " + recipe.getId() + " is archived and cannot be used by a product.");
        }

        return recipe;
    }

    private void assertNameIsFree(String name, UUID selfId) {
        products.findByName(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    if (existing.isActive()) {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PRODUCT_ALREADY_EXISTS,
                                "A product with this name already exists.");
                    }
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ARCHIVED_PRODUCT_EXISTS,
                            "An archived product with this name exists. Restore it instead");
                });
    }

    private List<ItemDraft> resolveDrafts(List<ProductItemRequest> items, Set<UUID> ingredientIdsAlreadyInProduct) {
        assertNoDuplicates(items);
        Map<UUID, Ingredient> byId = loadIngredients(items);

        return items.stream().map(item -> {
            Ingredient ingredient = byId.get(item.ingredientId());
            assertIngredientUsable(ingredient, ingredientIdsAlreadyInProduct);
            return new ItemDraft(ingredient, item.quantity());
        }).toList();
    }

    private void assertIngredientUsable(Ingredient ingredient, Set<UUID> ingredientIdsAlreadyInProduct) {
        if (!ingredient.isActive() && !ingredientIdsAlreadyInProduct.contains(ingredient.getId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ARCHIVED_INGREDIENT_NOT_ALLOWED,
                    "Ingredient " + ingredient.getId() + " is archived and cannot be added to a product.");
        }
    }

    private void assertNoDuplicates(List<ProductItemRequest> items) {
        Set<UUID> seen = new HashSet<>();
        for (ProductItemRequest item : items) {
            if (!seen.add(item.ingredientId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.DUPLICATE_PRODUCT_ITEM,
                        "Ingredient " + item.ingredientId() + " appears more than once.");
            }
        }
    }

    private Map<UUID, Ingredient> loadIngredients(List<ProductItemRequest> items) {
        Set<UUID> ids = items.stream().map(ProductItemRequest::ingredientId).collect(Collectors.toSet());
        Map<UUID, Ingredient> found = ingredients.findAllById(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));

        for (ProductItemRequest item : items) {
            if (!found.containsKey(item.ingredientId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INGREDIENT_NOT_FOUND,
                        "Ingredient " + item.ingredientId() + " not found.");
            }
        }

        return found;
    }
}
