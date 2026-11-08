package dev.peluci.doceenccoreapi.recipe;

import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.ingredient.IngredientRepository;
import dev.peluci.doceenccoreapi.recipe.Recipe.ItemDraft;
import dev.peluci.doceenccoreapi.recipe.dto.CreateRecipeRequest;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeDetailResponse;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeItemRequest;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeItemResponse;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeSimulationResponse;
import dev.peluci.doceenccoreapi.recipe.dto.RecipeSummaryResponse;
import dev.peluci.doceenccoreapi.recipe.dto.SimulateRecipeRequest;
import dev.peluci.doceenccoreapi.recipe.dto.UpdateRecipeRequest;
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
public class RecipeService {

    private final RecipeRepository recipes;
    private final IngredientRepository ingredients;
    private final CategoryRepository categories;
    private final AuditingHandler auditingHandler;

    public RecipeService(RecipeRepository recipes, IngredientRepository ingredients, CategoryRepository categories, AuditingHandler auditingHandler) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.categories = categories;
        this.auditingHandler = auditingHandler;
    }

    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> list() {
        return recipes.findAllWithItems().stream().map(RecipeSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RecipeDetailResponse get(UUID id) {
        return RecipeDetailResponse.from(load(id));
    }

    public RecipeDetailResponse create(CreateRecipeRequest request) {
        String name = request.name().strip();
        Category category = loadRecipeCategory(request.categoryId());
        assertNameIsFree(name, null);

        List<ItemDraft> drafts = resolveDrafts(request.items(), Set.of());

        Recipe recipe = new Recipe(name, category, request.yieldQuantity(), request.yieldUnit(), request.prepTimeMinutes(), request.shelfLifeDays());
        recipe.replaceItems(drafts);
        return RecipeDetailResponse.from(recipes.saveAndFlush(recipe));
    }

    public RecipeDetailResponse update(UUID id, UpdateRecipeRequest request) {
        Recipe recipe = load(id);
        if (!recipe.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RECIPE_ARCHIVED,
                    "An archived recipe cannot be changed. Restore it first.");
        }

        String name = request.name().strip();
        Category category = loadRecipeCategory(request.categoryId());
        assertNameIsFree(name, id);

        Set<UUID> currentIngredientIds = recipe.getItems().stream()
                .map(item -> item.getIngredient().getId())
                .collect(Collectors.toSet());
        List<ItemDraft> drafts = resolveDrafts(request.items(), currentIngredientIds);

        recipe.update(name, category, request.yieldQuantity(), request.prepTimeMinutes(), request.shelfLifeDays());
        if (recipe.replaceItems(drafts)) {
            auditingHandler.markModified(recipe);
        }
        return RecipeDetailResponse.from(recipes.saveAndFlush(recipe));
    }

    public RecipeDetailResponse archive(UUID id) {
        Recipe recipe = load(id);
        recipe.archive();
        return RecipeDetailResponse.from(recipes.saveAndFlush(recipe));
    }

    public RecipeDetailResponse restore(UUID id) {
        Recipe recipe = load(id);
        recipe.restore();
        return RecipeDetailResponse.from(recipes.saveAndFlush(recipe));
    }

    @Transactional(readOnly = true)
    public RecipeSimulationResponse simulate(SimulateRecipeRequest request) {
        assertNoDuplicates(request.items());
        Map<UUID, Ingredient> byId = loadIngredients(request.items());

        List<RecipeItemResponse> items = request.items().stream()
                .map(item -> RecipeItemResponse.of(byId.get(item.ingredientId()), item.quantity()))
                .sorted(RecipeItemResponse.byIngredientName())
                .toList();

        RecipeCostCalculator.Summary summary = RecipeCostCalculator.summarize(
                items.stream().map(RecipeItemResponse::cost).toList(),
                Scale.quantity(request.yieldQuantity()));
        return new RecipeSimulationResponse(items, summary.totalCost(), summary.costPerYieldUnit());
    }

    private Recipe load(UUID id) {
        return recipes.findWithItemsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RECIPE_NOT_FOUND,
                        "Recipe not found."));
    }

    private Category loadRecipeCategory(UUID categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.CATEGORY_NOT_FOUND,
                        "Category not found."));

        if (category.getType() != CategoryType.RECIPE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.CATEGORY_TYPE_MISMATCH,
                    "A recipe needs a category of type RECIPE.");
        }

        return category;
    }

    private void assertNameIsFree(String name, UUID selfId) {
        recipes.findByName(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    if (existing.isActive()) {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RECIPE_ALREADY_EXISTS,
                                "A recipe with this name already exists.");
                    }
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ARCHIVED_RECIPE_EXISTS,
                            "An archived recipe with this name exists. Restore it instead.");
                });
    }

    private List<ItemDraft> resolveDrafts(List<RecipeItemRequest> items, Set<UUID> ingredientIdsAlreadyInRecipe) {
        assertNoDuplicates(items);
        Map<UUID, Ingredient> byId = loadIngredients(items);

        return items.stream().map(item -> {
            Ingredient ingredient = byId.get(item.ingredientId());
            if (!ingredient.isActive() && !ingredientIdsAlreadyInRecipe.contains(ingredient.getId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.ARCHIVED_INGREDIENT_NOT_ALLOWED,
                        "Ingredient " + ingredient.getId() + " is archived and cannot be added to a recipe.");
            }
            return new ItemDraft(ingredient, item.quantity());
        }).toList();
    }

    private void assertNoDuplicates(List<RecipeItemRequest> items) {
        Set<UUID> seen = new HashSet<>();
        for (RecipeItemRequest item : items) {
            if (!seen.add(item.ingredientId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.DUPLICATE_RECIPE_ITEM,
                        "Ingredient " + item.ingredientId() + " appears more than once.");
            }
        }
    }

    private Map<UUID, Ingredient> loadIngredients(List<RecipeItemRequest> items) {
        Set<UUID> ids = items.stream().map(RecipeItemRequest::ingredientId).collect(Collectors.toSet());
        Map<UUID, Ingredient> found = ingredients.findAllById(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));

        for (RecipeItemRequest item : items) {
            if (!found.containsKey(item.ingredientId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INGREDIENT_NOT_FOUND,
                        "Ingredient " + item.ingredientId() + " not found.");
            }
        }

        return found;
    }

}
