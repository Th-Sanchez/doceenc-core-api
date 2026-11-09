package dev.peluci.doceenccoreapi.recipe;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.auth.UserRepository;
import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.support.DatabaseCleaner;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.TestAuthenticator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class RecipeApiIntegrationTest {

    private static final String BASE = "/api/v1/recipes";
    private static final String INGREDIENTS = "/api/v1/ingredients";
    private static final String HEADER = TestAuthenticator.REQUIRED_HEADER;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CategoryRepository categories;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    DatabaseCleaner cleaner;

    private Cookie session;
    private UUID sweets;
    private UUID flours;
    private UUID condensedMilk;
    private UUID cocoa;
    private UUID butter;

    @BeforeEach
    void setUp() throws Exception {
        cleaner.clean();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();

        sweets = categories.save(new Category("Doces", CategoryType.RECIPE)).getId();
        flours = categories.save(new Category("Farinhas", CategoryType.INGREDIENT)).getId();

        condensedMilk = createIngredient("Leite condensado", "20.00");
        cocoa = createIngredient("Cacau em pó", "90.00");
        butter = createIngredient("Manteiga", "60.00");
    }

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/simulation").header(HEADER, "test")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturnsTheLocationAndTheFullDetailWithCostComputedByTheServer() throws Exception {
        MvcResult result = doPost(BASE, recipeJson("Brigadeiro", sweets, "40", "UNIT", 30, 5,
                item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Brigadeiro"))
                .andExpect(jsonPath("$.category.name").value("Doces"))
                .andExpect(jsonPath("$.yieldQuantity").value(40.0))
                .andExpect(jsonPath("$.yieldUnit").value("UNIT"))
                .andExpect(jsonPath("$.prepTimeMinutes").value(30))
                .andExpect(jsonPath("$.shelfLifeDays").value(5))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].ingredient.name").value("Cacau em pó"))
                .andExpect(jsonPath("$.items[0].ingredient.baseUnit").value("GRAM"))
                .andExpect(jsonPath("$.items[0].ingredient.unitPrice").value(0.09))
                .andExpect(jsonPath("$.items[0].ingredient.active").value(true))
                .andExpect(jsonPath("$.items[0].quantity").value(30.0))
                .andExpect(jsonPath("$.items[0].cost").value(2.7))
                .andExpect(jsonPath("$.items[1].ingredient.name").value("Leite condensado"))
                .andExpect(jsonPath("$.items[1].cost").value(7.9))
                .andExpect(jsonPath("$.items[2].ingredient.name").value("Manteiga"))
                .andExpect(jsonPath("$.items[2].cost").value(1.2))
                .andExpect(jsonPath("$.totalCost").value(11.8))
                .andExpect(jsonPath("$.costPerYieldUnit").value(0.295))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        UUID id = idOf(result);
        assertThat(result.getResponse().getHeader("Location")).endsWith(BASE + "/" + id);
    }

    @Test
    void prepTimeAndShelfLifeAreOptional() throws Exception {
        doPost(BASE, recipeJson("Calda", sweets, "500", "MILLILITER", null, null, item(cocoa, "50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yieldUnit").value("MILLILITER"))
                .andExpect(jsonPath("$.prepTimeMinutes").doesNotExist())
                .andExpect(jsonPath("$.shelfLifeDays").doesNotExist());
    }

    @Test
    void getReturnsTheDetailAndAnUnknownIdIsNotFound() throws Exception {
        UUID id = createRecipe("Brigadeiro", item(condensedMilk, "395"));

        doGet(BASE + "/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.items.length()").value(1));

        doGet(BASE + "/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
    }

    @Test
    void listReturnsSummariesSortedByNameWithoutItemsAndIncludesArchivedOnes() throws Exception {
        createRecipe("Torta", item(butter, "100"));
        UUID brigadeiro = createRecipe("brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"));
        UUID archived = createRecipe("Antiga", item(cocoa, "10"));
        doPost(BASE + "/" + archived + "/archive", "").andExpect(status().isOk());

        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Antiga"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[1].id").value(brigadeiro.toString()))
                .andExpect(jsonPath("$[1].name").value("brigadeiro"))
                .andExpect(jsonPath("$[1].itemCount").value(2))
                .andExpect(jsonPath("$[1].archivedIngredientCount").value(0))
                .andExpect(jsonPath("$[1].totalCost").value(10.6))
                .andExpect(jsonPath("$[1].costPerYieldUnit").value(0.265))
                .andExpect(jsonPath("$[1].items").doesNotExist())
                .andExpect(jsonPath("$[2].name").value("Torta"));
    }

    @Test
    void listCountsTheArchivedIngredientsOfEachRecipe() throws Exception {
        createRecipe("Brigadeiro", item(condensedMilk, "395"), item(butter, "20"));
        createRecipe("Calda", item(cocoa, "50"));

        doPost(INGREDIENTS + "/" + butter + "/archive", "").andExpect(status().isOk());

        doGet(BASE)
                .andExpect(jsonPath("$[0].name").value("Brigadeiro"))
                .andExpect(jsonPath("$[0].itemCount").value(2))
                .andExpect(jsonPath("$[0].archivedIngredientCount").value(1))
                .andExpect(jsonPath("$[1].name").value("Calda"))
                .andExpect(jsonPath("$[1].archivedIngredientCount").value(0));
    }

    @Test
    void costFollowsTheIngredientsCurrentPrice() throws Exception {
        UUID id = createRecipe("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20"));
        doGet(BASE + "/" + id).andExpect(jsonPath("$.totalCost").value(11.8));

        doPost(INGREDIENTS + "/" + condensedMilk + "/prices", purchaseJson("40.00", "2026-10-20"))
                .andExpect(status().isCreated());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.totalCost").value(19.7))
                .andExpect(jsonPath("$.costPerYieldUnit").value(0.4925));
        doGet(BASE).andExpect(jsonPath("$[0].totalCost").value(19.7));
    }

    @Test
    void createValidationReportsFieldPaths() throws Exception {
        doPost(BASE, recipeJson("", sweets, "0", "UNIT", 0, null, item(cocoa, "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'yieldQuantity')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'prepTimeMinutes')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')]").exists());
    }

    @Test
    void createRejectsInvalidReferencesWithSpecificCodes() throws Exception {
        doPost(BASE, recipeJson("Dup", sweets, "10", "UNIT", null, null, item(cocoa, "10"), item(cocoa, "20")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RECIPE_ITEM"));

        doPost(BASE, recipeJson("Sem insumo", sweets, "10", "UNIT", null, null, item(UUID.randomUUID(), "10")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));

        doPost(BASE, recipeJson("Categoria de insumo", flours, "10", "UNIT", null, null, item(cocoa, "10")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_TYPE_MISMATCH"));

        doPost(BASE, recipeJson("Sem categoria", UUID.randomUUID(), "10", "UNIT", null, null, item(cocoa, "10")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        doGet(BASE).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nameIsUniqueIgnoringCaseAcrossActiveAndArchivedRecipes() throws Exception {
        UUID id = createRecipe("Brigadeiro", item(cocoa, "30"));

        doPost(BASE, recipeJson("BRIGADEIRO", sweets, "10", "UNIT", null, null, item(cocoa, "30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_ALREADY_EXISTS"));

        doPost(BASE + "/" + id + "/archive", "").andExpect(status().isOk());

        doPost(BASE, recipeJson("Brigadeiro", sweets, "10", "UNIT", null, null, item(cocoa, "30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHIVED_RECIPE_EXISTS"));
    }

    @Test
    void updateReplacesTheLinesAndKeepsTheYieldUnit() throws Exception {
        UUID id = createRecipe("Brigadeiro", item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20"));

        doPut(BASE + "/" + id, updateJson("Brigadeiro gourmet", sweets, "20", 45, null,
                item(condensedMilk, "395"), item(cocoa, "40")))
                .andExpect(jsonPath("$.name").value("Brigadeiro gourmet"))
                .andExpect(jsonPath("$.yieldQuantity").value(20.0))
                .andExpect(jsonPath("$.yieldUnit").value("UNIT"))
                .andExpect(jsonPath("$.prepTimeMinutes").value(45))
                .andExpect(jsonPath("$.shelfLifeDays").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalCost").value(11.5))
                .andExpect(jsonPath("$.costPerYieldUnit").value(0.575));

        doPut(BASE + "/" + UUID.randomUUID(), updateJson("X", sweets, "1", null, null, item(cocoa, "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
    }

    @Test
    void archivedRecipesAreReadOnlyUntilRestored() throws Exception {
        UUID id = createRecipe("Brigadeiro", item(cocoa, "30"));

        doPost(BASE + "/" + id + "/archive", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        doPost(BASE + "/" + id + "/archive", "").andExpect(status().isOk());

        doPut(BASE + "/" + id, updateJson("Brigadeiro", sweets, "40", null, null, item(cocoa, "35")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_ARCHIVED"));

        doPost(BASE + "/" + id + "/restore", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        doPut(BASE + "/" + id, updateJson("Brigadeiro", sweets, "40", null, null, item(cocoa, "35")))
                .andExpect(status().isOk());

        doPost(BASE + "/" + UUID.randomUUID() + "/archive", "").andExpect(status().isNotFound());
        doPost(BASE + "/" + UUID.randomUUID() + "/restore", "").andExpect(status().isNotFound());
    }

    @Test
    void anArchivedIngredientStaysInTheRecipesThatAlreadyUseItButCannotEnterOthers() throws Exception {
        UUID flour = createIngredient("Farinha", "4.00");
        UUID id = createRecipe("Bolo", item(condensedMilk, "395"), item(butter, "20"));

        doPost(INGREDIENTS + "/" + butter + "/archive", "").andExpect(status().isOk());
        doPost(INGREDIENTS + "/" + flour + "/archive", "").andExpect(status().isOk());

        doPut(BASE + "/" + id, updateJson("Bolo", sweets, "40", null, null,
                item(condensedMilk, "400"), item(butter, "25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].ingredient.name").value("Manteiga"))
                .andExpect(jsonPath("$.items[1].ingredient.active").value(false));

        doPut(BASE + "/" + id, updateJson("Bolo", sweets, "40", null, null,
                item(condensedMilk, "400"), item(butter, "25"), item(flour, "100")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_INGREDIENT_NOT_ALLOWED"));

        doPost(BASE, recipeJson("Outro bolo", sweets, "10", "UNIT", null, null, item(flour, "100")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_INGREDIENT_NOT_ALLOWED"));
    }

    @Test
    void simulationReturnsTheCostWithoutPersistingAnything() throws Exception {
        doPost(BASE + "/simulation", simulationJson("40",
                item(condensedMilk, "395"), item(cocoa, "30"), item(butter, "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].ingredient.name").value("Cacau em pó"))
                .andExpect(jsonPath("$.items[0].cost").value(2.7))
                .andExpect(jsonPath("$.totalCost").value(11.8))
                .andExpect(jsonPath("$.costPerYieldUnit").value(0.295));

        doGet(BASE).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void simulationMatchesTheSavedRecipeAndFlagsArchivedIngredients() throws Exception {
        UUID id = createRecipe("Brigadeiro", item(condensedMilk, "395"), item(butter, "20"));
        doPost(INGREDIENTS + "/" + butter + "/archive", "").andExpect(status().isOk());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.totalCost").value(9.1))
                .andExpect(jsonPath("$.costPerYieldUnit").value(0.2275));

        doPost(BASE + "/simulation", simulationJson("40", item(condensedMilk, "395"), item(butter, "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(9.1))
                .andExpect(jsonPath("$.costPerYieldUnit").value(0.2275))
                .andExpect(jsonPath("$.items[1].ingredient.active").value(false));
    }

    @Test
    void simulationAcceptsNoItemsAndRejectsInvalidInput() throws Exception {
        doPost(BASE + "/simulation", simulationJson("10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCost").value(0))
                .andExpect(jsonPath("$.costPerYieldUnit").value(0.0));

        doPost(BASE + "/simulation", simulationJson("0", item(cocoa, "10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'yieldQuantity')]").exists());

        doPost(BASE + "/simulation", simulationJson("10", item(cocoa, "10"), item(cocoa, "5")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RECIPE_ITEM"));

        doPost(BASE + "/simulation", simulationJson("10", item(UUID.randomUUID(), "10")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
    }

    private UUID createIngredient(String name, String pricePerKg) throws Exception {
        String body = doPost(INGREDIENTS, """
                {"name":"%s","brandSupplier":null,"categoryId":"%s","baseUnit":"GRAM","price":%s}"""
                .formatted(name, flours, purchaseJson(pricePerKg, "2026-10-01")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID createRecipe(String name, String... items) throws Exception {
        MvcResult result = doPost(BASE, recipeJson(name, sweets, "40", "UNIT", 30, 5, items))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private static UUID idOf(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static String purchaseJson(String packagePricePerKg, String purchasedOn) {
        return """
                {"packagePrice":%s,"packageQuantity":1,"unit":"KG","purchasedOn":"%s"}"""
                .formatted(packagePricePerKg, purchasedOn);
    }

    private static String item(UUID ingredientId, String quantity) {
        return """
                {"ingredientId":"%s","quantity":%s}""".formatted(ingredientId, quantity);
    }

    private static String recipeJson(String name, UUID categoryId, String yieldQuantity, String yieldUnit,
                                     Integer prepTimeMinutes, Integer shelfLifeDays, String... items) {
        return """
                {"name":"%s","categoryId":"%s","yieldQuantity":%s,"yieldUnit":"%s",
                "prepTimeMinutes":%s,"shelfLifeDays":%s,"items":[%s]}"""
                .formatted(name, categoryId, yieldQuantity, yieldUnit, prepTimeMinutes, shelfLifeDays,
                        String.join(",", items));
    }

    private static String updateJson(String name, UUID categoryId, String yieldQuantity,
                                     Integer prepTimeMinutes, Integer shelfLifeDays, String... items) {
        return """
                {"name":"%s","categoryId":"%s","yieldQuantity":%s,
                "prepTimeMinutes":%s,"shelfLifeDays":%s,"items":[%s]}"""
                .formatted(name, categoryId, yieldQuantity, prepTimeMinutes, shelfLifeDays,
                        String.join(",", items));
    }

    private static String simulationJson(String yieldQuantity, String... items) {
        return """
                {"yieldQuantity":%s,"items":[%s]}""".formatted(yieldQuantity, String.join(",", items));
    }

    private ResultActions doGet(String url) throws Exception {
        return mockMvc.perform(get(url).cookie(session));
    }

    private ResultActions doPost(String url, String body) throws Exception {
        return mockMvc.perform(post(url).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions doPut(String url, String body) throws Exception {
        return mockMvc.perform(put(url).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

}
