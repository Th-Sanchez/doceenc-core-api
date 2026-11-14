package dev.peluci.doceenccoreapi.product;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.support.DatabaseCleaner;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.TestAuthenticator;
import dev.peluci.doceenccoreapi.auth.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class ProductIntegrationTest {

    private static final String BASE = "/api/v1/products";
    private static final String HEADER = TestAuthenticator.REQUIRED_HEADER;

    private static final String UNIT_PACKAGE = "UNIT";

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

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Cookie session;
    private UUID cakes;
    private UUID mix;
    private UUID pot;
    private UUID potCake;

    @BeforeEach
    void setUp() throws Exception {
        cleaner.clean();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();

        UUID supplies = categories.save(new Category("Insumos", CategoryType.INGREDIENT)).getId();
        UUID packaging = categories.save(new Category("Embalagens", CategoryType.INGREDIENT)).getId();
        cakes = categories.save(new Category("Bolos", CategoryType.RECIPE)).getId();

        mix = createIngredient("Mistura para bolo", supplies, "GRAM", "68.00", "1", "KG");
        pot = createIngredient("Pote plástico", packaging, "UNIT", "7.00", "10", UNIT_PACKAGE);
        potCake = createRecipe("Bolo de pote", "10", "UNIT", mix, "1000");
    }

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void createDerivesCostMarginStatusAndSuggestedPricePerChannel() throws Exception {
        doPost(BASE, productJson("Bolo de pote Ninho", potCake, "1", "11.00", "9.00", item(pot, "1")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Bolo de pote Ninho"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.recipe.name").value("Bolo de pote"))
                .andExpect(jsonPath("$.recipe.active").value(true))
                .andExpect(jsonPath("$.recipe.yieldQuantity").value(10.0))
                .andExpect(jsonPath("$.recipe.yieldUnit").value("UNIT"))
                .andExpect(jsonPath("$.recipe.costPerYieldUnit").value(6.8))
                .andExpect(jsonPath("$.recipeQuantity").value(1.0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Pote plástico"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(0.7))
                .andExpect(jsonPath("$.items[0].cost").value(0.7))
                .andExpect(jsonPath("$.archivedIngredientCount").value(0))
                .andExpect(jsonPath("$.cost.recipeCost").value(6.8))
                .andExpect(jsonPath("$.cost.extrasCost").value(0.7))
                .andExpect(jsonPath("$.cost.totalCost").value(7.5))
                .andExpect(jsonPath("$.retail.targetMarginPercent").value(50.0))
                .andExpect(jsonPath("$.retail.price").value(11.0))
                .andExpect(jsonPath("$.retail.grossProfit").value(3.5))
                .andExpect(jsonPath("$.retail.marginPercent").value(31.82))
                .andExpect(jsonPath("$.retail.status").value("BELOW_TARGET"))
                .andExpect(jsonPath("$.retail.suggestedPrice").value(15.0))
                .andExpect(jsonPath("$.wholesale.targetMarginPercent").value(35.0))
                .andExpect(jsonPath("$.wholesale.price").value(9.0))
                .andExpect(jsonPath("$.wholesale.grossProfit").value(1.5))
                .andExpect(jsonPath("$.wholesale.marginPercent").value(16.67))
                .andExpect(jsonPath("$.wholesale.status").value("BELOW_TARGET"))
                .andExpect(jsonPath("$.wholesale.suggestedPrice").value(11.54));
    }

    @Test
    void aProductWithoutPricesIsNoPriceButStillGetsASuggestion() throws Exception {
        doPost(BASE, productJson("Bolo de pote", potCake, "1", null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.cost.extrasCost").value(0.0))
                .andExpect(jsonPath("$.cost.totalCost").value(6.8))
                .andExpect(jsonPath("$.retail.status").value("NO_PRICE"))
                .andExpect(jsonPath("$.retail.price").doesNotExist())
                .andExpect(jsonPath("$.retail.marginPercent").doesNotExist())
                .andExpect(jsonPath("$.retail.suggestedPrice").value(13.6))
                .andExpect(jsonPath("$.wholesale.status").value("NO_PRICE"))
                .andExpect(jsonPath("$.wholesale.suggestedPrice").value(10.47));
    }

    @Test
    void aPriceBelowTheCostIsALoss() throws Exception {
        doPost(BASE, productJson("Bolo de pote", potCake, "1", "7.00", null, item(pot, "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retail.status").value("LOSS"))
                .andExpect(jsonPath("$.retail.grossProfit").value(-0.5))
                .andExpect(jsonPath("$.retail.marginPercent").value(-7.14));
    }

    @Test
    void theRecipeQuantityScalesTheRecipePortionInTheRecipesOwnUnit() throws Exception {
        UUID byWeight = createRecipe("Massa base", "1000", "GRAM", mix, "1000");

        doPost(BASE, productJson("Mini bolo", byWeight, "20", "5.00", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipe.yieldUnit").value("GRAM"))
                .andExpect(jsonPath("$.recipeQuantity").value(20.0))
                .andExpect(jsonPath("$.cost.recipeCost").value(1.36))
                .andExpect(jsonPath("$.cost.totalCost").value(1.36));
    }

    @Test
    void changingTheTargetMarginsReclassifiesExistingProducts() throws Exception {
        UUID id = createProduct("Bolo de pote", "11.00", "9.00", item(pot, "1"));

        doGet(BASE + "/" + id).andExpect(jsonPath("$.retail.status").value("BELOW_TARGET"));

        doPut("/api/v1/settings", """
                {"targetMarginRetail":30.00,"targetMarginWholesale":10.00}""")
                .andExpect(status().isOk());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.retail.targetMarginPercent").value(30.0))
                .andExpect(jsonPath("$.retail.status").value("ON_TARGET"))
                .andExpect(jsonPath("$.retail.suggestedPrice").value(10.72))
                .andExpect(jsonPath("$.wholesale.status").value("ON_TARGET"));
    }

    @Test
    void theCostFollowsTheIngredientPrices() throws Exception {
        UUID id = createProduct("Bolo de pote", "15.00", null, item(pot, "1"));

        doPost("/api/v1/ingredients/" + pot + "/prices", """
                {"packagePrice":10.00,"packageQuantity":10,"unit":"%s","purchasedOn":"2026-10-20"}"""
                .formatted(UNIT_PACKAGE))
                .andExpect(status().isCreated());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.cost.extrasCost").value(1.0))
                .andExpect(jsonPath("$.cost.totalCost").value(7.8));
    }

    @Test
    void createValidationReportsFieldPaths() throws Exception {
        doPost(BASE, productJson("Bolo", potCake, "0", "11.00", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'recipeQuantity')]").exists());

        doPost(BASE, productJson("Bolo", potCake, "1", "0", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'retailPrice')]").exists());

        doPost(BASE, productJson("Bolo", potCake, "1", "10.123", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'retailPrice')]").exists());

        doPost(BASE, productJson("Bolo", potCake, "1", null, null, item(pot, "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')]").exists());

        doPost(BASE, """
                {"name":"Bolo","recipeId":"%s","recipeQuantity":1}""".formatted(potCake))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items')]").exists());
    }

    @Test
    void unknownReferencesAndDuplicateItemsAreUnprocessable() throws Exception {
        doPost(BASE, productJson("Bolo", UUID.randomUUID(), "1", null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));

        doPost(BASE, productJson("Bolo", potCake, "1", null, null, item(UUID.randomUUID(), "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));

        doPost(BASE, productJson("Bolo", potCake, "1", null, null, item(pot, "1"), item(pot, "2")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_ITEM"));

        doGet(BASE).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nameIdentityIgnoresCaseAndArchivedProductsKeepTheirName() throws Exception {
        UUID id = createProduct("Bolo de pote", null, null);

        doPost(BASE, productJson("BOLO DE POTE", potCake, "1", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ALREADY_EXISTS"));

        doPut(BASE + "/" + id, productJson("bolo de pote", potCake, "1", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("bolo de pote"));

        doPost(BASE + "/" + id + "/archive", "").andExpect(status().isOk());

        doPost(BASE, productJson("Bolo de pote", potCake, "1", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHIVED_PRODUCT_EXISTS"));
    }

    @Test
    void updateReplacesTheItemsAndCanClearAPrice() throws Exception {
        UUID id = createProduct("Bolo de pote", "11.00", "9.00", item(pot, "1"));

        doPut(BASE + "/" + id, productJson("Bolo de pote", potCake, "1", null, "9.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.cost.extrasCost").value(0.0))
                .andExpect(jsonPath("$.cost.totalCost").value(6.8))
                .andExpect(jsonPath("$.retail.status").value("NO_PRICE"))
                .andExpect(jsonPath("$.wholesale.price").value(9.0));
    }

    @Test
    void addingOnlyAnExtraItemStillTouchesUpdatedAt() throws Exception {
        String created = doPost(BASE, productJson("Bolo de pote", potCake, "1", "11.00", null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));
        Instant before = Instant.parse(JsonPath.read(created, "$.updatedAt"));

        String updated = doPut(BASE + "/" + id, productJson("Bolo de pote", potCake, "1", "11.00", null, item(pot, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(JsonPath.read(updated, "$.updatedAt"))).isAfter(before);
    }

    @Test
    void anArchivedProductCannotBeChangedUntilRestored() throws Exception {
        UUID id = createProduct("Bolo de pote", "11.00", null);

        doPost(BASE + "/" + id + "/archive", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        doPost(BASE + "/" + id + "/archive", "").andExpect(status().isOk());

        doPut(BASE + "/" + id, productJson("Bolo de pote", potCake, "1", "12.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ARCHIVED"));

        doPost(BASE + "/" + id + "/restore", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        doPut(BASE + "/" + id, productJson("Bolo de pote", potCake, "1", "12.00", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retail.price").value(12.0));
    }

    @Test
    void unknownProductsAreNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        doGet(BASE + "/" + unknown)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        doPut(BASE + "/" + unknown, productJson("Bolo", potCake, "1", null, null))
                .andExpect(status().isNotFound());
        doPost(BASE + "/" + unknown + "/archive", "").andExpect(status().isNotFound());
        doPost(BASE + "/" + unknown + "/restore", "").andExpect(status().isNotFound());
    }

    @Test
    void anArchivedRecipeCannotBeAttachedAnewButExistingProductsKeepWorking() throws Exception {
        UUID id = createProduct("Bolo de pote", "11.00", null, item(pot, "1"));
        doPost("/api/v1/recipes/" + potCake + "/archive", "").andExpect(status().isOk());

        doGet(BASE + "/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.active").value(false))
                .andExpect(jsonPath("$.cost.totalCost").value(7.5));

        doPut(BASE + "/" + id, productJson("Bolo de pote", potCake, "1", "12.00", null, item(pot, "1")))
                .andExpect(status().isOk());

        doPost(BASE, productJson("Outro bolo", potCake, "1", null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_RECIPE_NOT_ALLOWED"));
    }

    @Test
    void anArchivedIngredientStaysInExistingProductsIsCountedAndCannotBeAddedAnew() throws Exception {
        UUID id = createProduct("Bolo de pote", "11.00", null, item(pot, "1"));
        doPost("/api/v1/ingredients/" + pot + "/archive", "").andExpect(status().isOk());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.archivedIngredientCount").value(1))
                .andExpect(jsonPath("$.cost.totalCost").value(7.5));

        doPut(BASE + "/" + id, productJson("Bolo de pote", potCake, "1", "11.00", null, item(pot, "1")))
                .andExpect(status().isOk());

        doPost(BASE, productJson("Outro bolo", potCake, "1", null, null, item(pot, "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_INGREDIENT_NOT_ALLOWED"));
    }

    @Test
    void listSortsIgnoringCaseAndCarriesCostAndBothChannels() throws Exception {
        createProduct("Cento de brigadeiro", "160.00", "120.00");
        createProduct("brownie", "8.50", null);
        createProduct("Bolo de pote", "11.00", "9.00", item(pot, "1"));

        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Bolo de pote"))
                .andExpect(jsonPath("$[1].name").value("brownie"))
                .andExpect(jsonPath("$[2].name").value("Cento de brigadeiro"))
                .andExpect(jsonPath("$[0].cost.totalCost").value(7.5))
                .andExpect(jsonPath("$[0].retail.status").value("BELOW_TARGET"))
                .andExpect(jsonPath("$[0].wholesale.status").value("BELOW_TARGET"))
                .andExpect(jsonPath("$[1].wholesale.status").value("NO_PRICE"))
                .andExpect(jsonPath("$[0].archivedIngredientCount").value(0));
    }

    @Test
    void simulationComputesWithoutSavingAnything() throws Exception {
        doPost(BASE + "/simulation", simulationJson(potCake, "1", "15.00", "11.54", item(pot, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.name").value("Bolo de pote"))
                .andExpect(jsonPath("$.items[0].name").value("Pote plástico"))
                .andExpect(jsonPath("$.cost.totalCost").value(7.5))
                .andExpect(jsonPath("$.retail.status").value("ON_TARGET"))
                .andExpect(jsonPath("$.retail.marginPercent").value(50.0))
                .andExpect(jsonPath("$.wholesale.status").value("ON_TARGET"))
                .andExpect(jsonPath("$.wholesale.marginPercent").value(35.01));

        doGet(BASE).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void simulationRejectsInvalidInputLikeCreate() throws Exception {
        doPost(BASE + "/simulation", """
                {"recipeQuantity":1,"items":[]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'recipeId')]").exists());

        doPost(BASE + "/simulation", simulationJson(UUID.randomUUID(), "1", null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));

        doPost("/api/v1/recipes/" + potCake + "/archive", "").andExpect(status().isOk());
        doPost(BASE + "/simulation", simulationJson(potCake, "1", null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_RECIPE_NOT_ALLOWED"));
    }

    @Test
    void listingDoesNotIssueMoreQueriesWhenThereAreMoreProducts() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            for (int i = 1; i <= 3; i++) {
                createProductWithItsOwnRecipe(i);
            }
            long withThree = statementsForList(statistics, 3);

            for (int i = 4; i <= 12; i++) {
                createProductWithItsOwnRecipe(i);
            }
            long withTwelve = statementsForList(statistics, 12);

            assertThat(withTwelve).isEqualTo(withThree);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private long statementsForList(Statistics statistics, int expectedProducts) throws Exception {
        doGet(BASE).andExpect(status().isOk());
        statistics.clear();
        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expectedProducts));
        return statistics.getPrepareStatementCount();
    }

    private void createProductWithItsOwnRecipe(int number) throws Exception {
        UUID recipe = createRecipe("Receita " + number, "10", "UNIT", mix, "1000");
        doPost(BASE, productJson("Produto " + number, recipe, "1", "15.00", "11.00", item(pot, "1")))
                .andExpect(status().isCreated());
    }

    private UUID createProduct(String name, String retail, String wholesale, String... items) throws Exception {
        String body = doPost(BASE, productJson(name, potCake, "1", retail, wholesale, items))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID createIngredient(String name, UUID categoryId, String baseUnit,
                                  String price, String quantity, String unit) throws Exception {
        String body = doPost("/api/v1/ingredients", """
                {"name":"%s","brandSupplier":null,"categoryId":"%s","baseUnit":"%s",
                "price":{"packagePrice":%s,"packageQuantity":%s,"unit":"%s","purchasedOn":"2026-10-01"}}"""
                .formatted(name, categoryId, baseUnit, price, quantity, unit))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID createRecipe(String name, String yieldQuantity, String yieldUnit,
                              UUID ingredientId, String quantity) throws Exception {
        String body = doPost("/api/v1/recipes", """
                {"name":"%s","categoryId":"%s","yieldQuantity":%s,"yieldUnit":"%s",
                "items":[{"ingredientId":"%s","quantity":%s}]}"""
                .formatted(name, cakes, yieldQuantity, yieldUnit, ingredientId, quantity))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String productJson(String name, UUID recipeId, String recipeQuantity,
                                      String retail, String wholesale, String... items) {
        return """
                {"name":"%s","recipeId":"%s","recipeQuantity":%s,"retailPrice":%s,"wholesalePrice":%s,
                "items":[%s]}""".formatted(name, recipeId, recipeQuantity, nullable(retail), nullable(wholesale),
                String.join(",", items));
    }

    private static String simulationJson(UUID recipeId, String recipeQuantity,
                                         String retail, String wholesale, String... items) {
        return """
                {"recipeId":"%s","recipeQuantity":%s,"retailPrice":%s,"wholesalePrice":%s,
                "items":[%s]}""".formatted(recipeId, recipeQuantity, nullable(retail), nullable(wholesale),
                String.join(",", items));
    }

    private static String item(UUID ingredientId, String quantity) {
        return """
                {"ingredientId":"%s","quantity":%s}""".formatted(ingredientId, quantity);
    }

    private static String nullable(String number) {
        return number == null ? "null" : number;
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