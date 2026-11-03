package dev.peluci.doceenccoreapi.ingredient;

import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.ingredient.dto.CreateIngredientRequest;
import dev.peluci.doceenccoreapi.ingredient.dto.IngredientResponse;
import dev.peluci.doceenccoreapi.ingredient.dto.PriceHistoryResponse;
import dev.peluci.doceenccoreapi.ingredient.dto.PriceRequest;
import dev.peluci.doceenccoreapi.ingredient.dto.UpdateIngredientRequest;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import dev.peluci.doceenccoreapi.shared.numeric.Scale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class IngredientService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Comparator<IngredientPriceHistory> NEWEST_FIRST =
            Comparator.comparing(IngredientPriceHistory::getPurchasedOn)
                    .thenComparing(IngredientPriceHistory::getCreatedAt)
                    .reversed();

    private final IngredientRepository ingredients;
    private final IngredientPriceHistoryRepository history;
    private final CategoryRepository categories;
    private final Clock clock;

    public IngredientService(IngredientRepository ingredients, IngredientPriceHistoryRepository history, CategoryRepository categories, Clock clock) {
        this.ingredients = ingredients;
        this.history = history;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> list(UUID categoryId, Boolean active, String text) {
        return toResponses(ingredients.findAll(
                IngredientSpecifications.filter(categoryId, active, text),
                Sort.by(Sort.Order.asc("name").ignoreCase())));
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> priceHistory(UUID id) {
        if (!ingredients.existsById(id)) {
            throw notFound();
        }
        return history.findByIngredientIdOrderByPurchasedOnDescCreatedAtDesc(id).stream()
                .map(PriceHistoryResponse::from).toList();
    }

    public IngredientResponse create(CreateIngredientRequest request) {
        String name = request.name();
        String brand = normalizeBrand(request.brandSupplier());
        Category category = ingredientCategory(request.categoryId());
        assertIdentityIsFree(name, brand, null);

        IngredientPrice price = toPrice(request.price(), request.baseUnit());
        LocalDate purchasedOn = purchaseDate(request.price().purchasedOn());

        Ingredient ingredient = new Ingredient(name, brand, category, request.baseUnit(), price);
        try {
            ingredients.saveAndFlush(ingredient);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INGREDIENT_ALREADY_EXISTS,
                    "An ingredient with this name and brand already exists.");
        }
        history.save(new IngredientPriceHistory(ingredient, price, purchasedOn));
        return toResponse(ingredient);
    }

    public IngredientResponse update(UUID id, UpdateIngredientRequest request) {
        Ingredient ingredient = findWithCategory(id);
        String name = request.name().trim();
        String brand = normalizeBrand(request.brandSupplier());
        Category category = ingredientCategory(request.categoryId());
        assertIdentityIsFree(name, brand, id);

        ingredient.update(name, brand, category);
        try {
            ingredients.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INGREDIENT_ALREADY_EXISTS,
                    "An ingredient with this name and brand already exists.");
        }
        return toResponse(ingredient);
    }

    public IngredientResponse registerPurchase(UUID id, PriceRequest request) {
        Ingredient ingredient = findWithCategory(id);
        IngredientPrice price = toPrice(request, ingredient.getBaseUnit());
        LocalDate purchasedOn = purchaseDate(request.purchasedOn());

        Optional<LocalDate> latest = history.findLatestPurchaseDate(id);
        history.save(new IngredientPriceHistory(ingredient, price, purchasedOn));

        boolean isNewestPurchase = latest.isEmpty() || !purchasedOn.isBefore(latest.get());
        if (isNewestPurchase) {
            ingredient.changePrice(price);
        }
        return toResponse(ingredient);
    }

    public IngredientResponse archive(UUID id) {
        Ingredient ingredient = findWithCategory(id);
        ingredient.archive();
        return toResponse(ingredient);
    }

    public IngredientResponse restore(UUID id) {
        Ingredient ingredient = findWithCategory(id);
        ingredient.restore();
        return toResponse(ingredient);
    }

    private IngredientResponse toResponse(Ingredient ingredient) {
        return toResponses(List.of(ingredient)).getFirst();
    }

    private List<IngredientResponse> toResponses(List<Ingredient> list) {
        if (list.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = list.stream().map(Ingredient::getId).toList();
        Map<UUID, List<IngredientPriceHistory>> byIngredient = history.findLatestTwoPerIngredient(ids).stream()
                .collect(Collectors.groupingBy(IngredientPriceHistory::getIngredientId));

        return list.stream().map(ingredient -> {
            List<IngredientPriceHistory> latest = byIngredient.getOrDefault(ingredient.getId(), List.of())
                    .stream().sorted(NEWEST_FIRST).toList();
            LocalDate lastPurchasedOn = latest.isEmpty() ? null : latest.get(0).getPurchasedOn();
            BigDecimal variation = latest.size() < 2 ? null
                    : variationPercent(ingredient.getPrice().getUnitPrice(), latest.get(1).getPrice().getUnitPrice());
            return IngredientResponse.from(ingredient, lastPurchasedOn, variation);
        }).toList();
    }

    private static BigDecimal variationPercent(BigDecimal current, BigDecimal previous) {
        return Scale.percentage(Scale.divide(current.subtract(previous), previous).multiply(HUNDRED));
    }

    private IngredientPrice toPrice(PriceRequest request, BaseUnit baseUnit) {
        return IngredientPrice.of(request.packagePrice(), request.packageQuantity(), request.unit(), baseUnit);
    }

    private LocalDate purchaseDate(LocalDate requested) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PURCHASE_DATE_IN_FUTURE,
                    "Purchase date cannot be in the future.");
        }
        return requested;
    }

    private Category ingredientCategory(UUID categoryId) {
        Category category = categories.findById(categoryId).orElseThrow(() ->
                new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.CATEGORY_NOT_FOUND, "Category not found."));

        if (category.getType() != CategoryType.INGREDIENT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.CATEGORY_TYPE_MISMATCH,
                    "Category is not an ingredient category.");
        }

        return category;
    }

    private void assertIdentityIsFree(String name, String brand, UUID selfId) {
        ingredients.findByIdentity(name, brand == null ? "" : brand)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    if (existing.isActive()) {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INGREDIENT_ALREADY_EXISTS,
                                "An ingredient with this name and brand already exists.");
                    }
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ARCHIVED_INGREDIENT_EXISTS,
                            "An archived ingredient with this name and brand exists. Restore it instead.");
                });
    }

    private static String normalizeBrand(String brand) {
        return brand == null || brand.isBlank() ? null : brand.trim();
    }

    private Ingredient findWithCategory(UUID id) {
        return ingredients.findWithCategoryById(id).orElseThrow(IngredientService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.INGREDIENT_NOT_FOUND, "Ingredient not found.");
    }
}
