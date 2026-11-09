package dev.peluci.doceenccoreapi.recipe;

import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.ingredient.BaseUnit;
import dev.peluci.doceenccoreapi.ingredient.Ingredient;
import dev.peluci.doceenccoreapi.ingredient.IngredientPrice;
import dev.peluci.doceenccoreapi.ingredient.IngredientRepository;
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
import dev.peluci.doceenccoreapi.support.DatabaseCleaner;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.MutableClock;
import jakarta.persistence.EntityManagerFactory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class RecipeIntegrationTest {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private IngredientRepository ingredients;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private MutableClock clock;

    private Category recipeCategory;
    private Category ingredientCategory;
    private Ingredient condensedMilk;
    private Ingredient cocoa;
    private Ingredient butter;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        recipeCategory = categories.saveAndFlush(new Category("Categoria de receita", CategoryType.RECIPE));
        ingredientCategory = categories.saveAndFlush(new Category("Categoria de insumo", CategoryType.INGREDIENT));
        condensedMilk = ingredient("Leite condensado", "0.020000");
        cocoa = ingredient("Cacau em pó", "0.090000");
        butter = ingredient("Manteiga", "0.060000");
    }

    @Test
    void createPersistsTheRecipeAndComputesCostOnRead() {
        RecipeDetailResponse created = recipeService.create(
                createRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20")));

        RecipeDetailResponse detail = recipeService.get(created.id());

        assertThat(detail.totalCost()).isEqualByComparingTo("11.80");
        assertThat(detail.costPerYieldUnit()).isEqualByComparingTo("0.295");
        assertThat(detail.items()).extracting(i -> i.ingredient().name())
                .containsExactly("Cacau em pó", "Leite condensado", "Manteiga");
    }

    @Test
    void ingredientPriceChangeIsReflectedInTheRecipeCost() {
        RecipeDetailResponse created = recipeService.create(
                createRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20")));

        Ingredient managed = ingredients.findById(condensedMilk.getId()).orElseThrow();
        managed.changePrice(price("0.030000"));
        ingredients.saveAndFlush(managed);

        assertThat(recipeService.get(created.id()).totalCost()).isEqualByComparingTo("15.75");
    }

    @Test
    void listReturnsSummariesWithCostInASingleQuery() {
        recipeService.create(createRequest("Receita A", item(condensedMilk, "100"), item(cocoa, "10")));
        recipeService.create(createRequest("Receita B", item(butter, "50"), item(cocoa, "20")));
        recipeService.create(createRequest("Receita C", item(condensedMilk, "200")));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<RecipeSummaryResponse> list = recipeService.list();

        assertThat(list).extracting(RecipeSummaryResponse::name)
                .containsExactly("Receita A", "Receita B", "Receita C");
        assertThat(statistics.getPrepareStatementCount())
                .as("listing must not issue one query per recipe or per item")
                .isEqualTo(1);
    }

    @Test
    void changingOnlyOneQuantityUpdatesInPlaceWithoutViolatingTheUniqueConstraint() {
        RecipeDetailResponse created = recipeService.create(
                createRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20")));

        recipeService.update(created.id(),
                updateRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "40"), item(butter, "20")));

        RecipeDetailResponse reloaded = recipeService.get(created.id());
        assertThat(reloaded.items()).hasSize(3);
        assertThat(quantityOf(reloaded, "Cacau em pó")).isEqualByComparingTo("40");
        assertThat(reloaded.totalCost()).isEqualByComparingTo("12.70");
    }

    @Test
    void removingOneLineAndAddingAnotherInTheSameCallWorks() {
        Ingredient sugar = ingredient("Açúcar", "0.005000");
        RecipeDetailResponse created = recipeService.create(
                createRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20")));

        recipeService.update(created.id(),
                updateRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"), item(sugar, "50")));

        RecipeDetailResponse reloaded = recipeService.get(created.id());
        assertThat(reloaded.items()).extracting(i -> i.ingredient().name())
                .containsExactlyInAnyOrder("Leite condensado", "Cacau em pó", "Açúcar");
    }

    @Test
    void updatedAtAdvancesWhenOnlyTheItemsChange() {
        RecipeDetailResponse created = recipeService.create(
                createRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30")));
        clock.advance(Duration.ofMinutes(5));

        recipeService.update(created.id(),
                updateRequest("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "35")));

        RecipeDetailResponse reloaded = recipeService.get(created.id());
        assertThat(reloaded.updatedAt()).isAfter(reloaded.createdAt());
    }

    @Test
    void archivedIngredientCannotBeAddedToANewRecipe() {
        archiveIngredient(butter);

        assertFails(() -> recipeService.create(createRequest("Brigadeiro", item(butter, "20"))),
                ErrorCode.ARCHIVED_INGREDIENT_NOT_ALLOWED);
    }

    @Test
    void archivedIngredientAlreadyInTheRecipeStaysButNewArchivedOnesAreRejected() {
        Ingredient flour = ingredient("Farinha", "0.004000");
        RecipeDetailResponse created = recipeService.create(
                createRequest("Bolo", item(condensedMilk, "395"), item(butter, "20")));

        archiveIngredient(butter);
        archiveIngredient(flour);

        RecipeDetailResponse updated = recipeService.update(created.id(),
                updateRequest("Bolo", item(condensedMilk, "400"), item(butter, "25")));
        assertThat(updated.items()).anySatisfy(i -> {
            assertThat(i.ingredient().name()).isEqualTo("Manteiga");
            assertThat(i.ingredient().active()).isFalse();
        });

        assertFails(() -> recipeService.update(created.id(),
                        updateRequest("Bolo", item(condensedMilk, "400"), item(butter, "25"), item(flour, "10"))),
                ErrorCode.ARCHIVED_INGREDIENT_NOT_ALLOWED);
    }

    @Test
    void duplicateNameIsRejectedWhetherTheExistingRecipeIsActiveOrArchived() {
        RecipeDetailResponse created = recipeService.create(createRequest("Brigadeiro", item(cocoa, "30")));

        assertFails(() -> recipeService.create(createRequest("brigadeiro", item(cocoa, "30"))),
                ErrorCode.RECIPE_ALREADY_EXISTS);

        recipeService.archive(created.id());

        assertFails(() -> recipeService.create(createRequest("Brigadeiro", item(cocoa, "30"))),
                ErrorCode.ARCHIVED_RECIPE_EXISTS);
    }

    @Test
    void archivedRecipeIsReadOnlyUntilRestored() {
        RecipeDetailResponse created = recipeService.create(createRequest("Brigadeiro", item(cocoa, "30")));

        assertThat(recipeService.archive(created.id()).active()).isFalse();

        assertFails(() -> recipeService.update(created.id(), updateRequest("Brigadeiro", item(cocoa, "35"))),
                ErrorCode.RECIPE_ARCHIVED);

        assertThat(recipeService.restore(created.id()).active()).isTrue();

        RecipeDetailResponse updated = recipeService.update(created.id(),
                updateRequest("Brigadeiro", item(cocoa, "35")));
        assertThat(quantityOf(updated, "Cacau em pó")).isEqualByComparingTo("35");
    }

    @Test
    void invalidReferencesAreRejectedWithSpecificCodes() {
        assertFails(() -> recipeService.create(
                        createRequest("Dup", item(cocoa, "10"), item(cocoa, "20"))),
                ErrorCode.DUPLICATE_RECIPE_ITEM);

        assertFails(() -> recipeService.create(
                        new CreateRecipeRequest("Sem insumo", recipeCategory.getId(), new BigDecimal("10"),
                                BaseUnit.UNIT, null, null,
                                List.of(new RecipeItemRequest(UUID.randomUUID(), new BigDecimal("10"))))),
                ErrorCode.INGREDIENT_NOT_FOUND);

        assertFails(() -> recipeService.create(
                        new CreateRecipeRequest("Categoria errada", ingredientCategory.getId(), new BigDecimal("10"),
                                BaseUnit.UNIT, null, null, List.of(item(cocoa, "10")))),
                ErrorCode.CATEGORY_TYPE_MISMATCH);
    }

    @Test
    void simulationMatchesTheCostOfTheSavedRecipe() {
        List<RecipeItemRequest> items = List.of(item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20"));
        RecipeDetailResponse saved = recipeService.create(
                new CreateRecipeRequest("Brigadeiro", recipeCategory.getId(), new BigDecimal("40"),
                        BaseUnit.UNIT, 30, 5, items));

        RecipeSimulationResponse simulation = recipeService.simulate(
                new SimulateRecipeRequest(new BigDecimal("40"), items));

        assertThat(simulation.totalCost()).isEqualTo(saved.totalCost());
        assertThat(simulation.costPerYieldUnit()).isEqualTo(saved.costPerYieldUnit());
        assertThat(simulation.items()).extracting(RecipeItemResponse::cost)
                .containsExactlyElementsOf(saved.items().stream().map(RecipeItemResponse::cost).toList());
    }

    @Test
    void simulationWithoutItemsReturnsZeros() {
        RecipeSimulationResponse simulation = recipeService.simulate(
                new SimulateRecipeRequest(new BigDecimal("10"), List.of()));

        assertThat(simulation.items()).isEmpty();
        assertThat(simulation.totalCost()).isEqualByComparingTo("0");
        assertThat(simulation.costPerYieldUnit()).isEqualByComparingTo("0");
    }

    @Test
    void simulationDoesNotApplyTheArchivedIngredientRuleButFlagsTheIngredient() {
        archiveIngredient(butter);

        RecipeSimulationResponse simulation = recipeService.simulate(
                new SimulateRecipeRequest(new BigDecimal("10"), List.of(item(butter, "20"))));

        assertThat(simulation.items()).singleElement()
                .satisfies(i -> assertThat(i.ingredient().active()).isFalse());
    }

    private Ingredient ingredient(String name, String unitPrice) {
        return ingredients.saveAndFlush(new Ingredient(name, null, ingredientCategory, BaseUnit.GRAM,
                price(unitPrice)));
    }

    private static IngredientPrice price(String unitPrice) {
        return new IngredientPrice(new BigDecimal("10.00"), new BigDecimal("500.0000"), new BigDecimal(unitPrice));
    }

    private void archiveIngredient(Ingredient ingredient) {
        Ingredient managed = ingredients.findById(ingredient.getId()).orElseThrow();
        managed.archive();
        ingredients.saveAndFlush(managed);
    }

    private CreateRecipeRequest createRequest(String name, RecipeItemRequest... items) {
        return new CreateRecipeRequest(name, recipeCategory.getId(), new BigDecimal("40"), BaseUnit.UNIT,
                30, 5, List.of(items));
    }

    private UpdateRecipeRequest updateRequest(String name, RecipeItemRequest... items) {
        return new UpdateRecipeRequest(name, recipeCategory.getId(), new BigDecimal("40"),
                30, 5, List.of(items));
    }

    private static RecipeItemRequest item(Ingredient ingredient, String quantity) {
        return new RecipeItemRequest(ingredient.getId(), new BigDecimal(quantity));
    }

    private static BigDecimal quantityOf(RecipeDetailResponse detail, String ingredientName) {
        return detail.items().stream()
                .filter(i -> i.ingredient().name().equals(ingredientName))
                .findFirst().orElseThrow()
                .quantity();
    }

    private void assertFails(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(codeOf(e)).isEqualTo(expected));
    }

    private static ErrorCode codeOf(ApiException e) {
        return e.getCode();
    }
}