package dev.peluci.doceenccoreapi.ingredient;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@IntegrationTest
public class IngredientIntegrationTest {

    private static final String BASE = "/api/v1/ingredients";
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
    private UUID flours;
    private UUID cakes;

    @BeforeEach
    void setUp() throws Exception {
        cleaner.clean();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();
        flours = categories.save(new Category("Farinhas", CategoryType.INGREDIENT)).getId();
        cakes = categories.save(new Category("Bolos", CategoryType.RECIPE)).getId();
    }

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void createDerivesTheUnitPriceAndRecordsTheFirstPurchase() throws Exception {
        String body = doPost(BASE, ingredientJson("Farinha de trigo", "Dona Benta", flours, "GRAM",
                "4.99", "1", "KG", "2026-10-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Farinha de trigo"))
                .andExpect(jsonPath("$.brandSupplier").value("Dona Benta"))
                .andExpect(jsonPath("$.category.name").value("Farinhas"))
                .andExpect(jsonPath("$.packagePrice").value(4.99))
                .andExpect(jsonPath("$.packageQuantity").value(1000.0))
                .andExpect(jsonPath("$.unitPrice").value(0.00499))
                .andExpect(jsonPath("$.lastPurchasedOn").value("2026-10-01"))
                .andExpect(jsonPath("$.variationPercent").doesNotExist())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        doGet(BASE + "/" + id + "/prices")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].purchasedOn").value("2026-10-01"));
    }

    @Test
    void createWithoutPurchaseDateDefaultsToToday() throws Exception {
        String body = doPost(BASE, ingredientJson("Açúcar", null, flours, "GRAM", "5.00", "1", "KG", null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        LocalDate date = LocalDate.parse(JsonPath.read(body, "$.lastPurchasedOn"));
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);
        assertThat(date).isBetween(utcToday.minusDays(1), utcToday.plusDays(1));
    }

    @Test
    void createRejectsAnIncompatibleUnitACategoryOfAnotherTypeAndAnUnknownCategory() throws Exception {
        doPost(BASE, ingredientJson("Leite", null, flours, "MILLILITER", "5.00", "1", "KG", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_UNIT"));

        doPost(BASE, ingredientJson("Leite", null, cakes, "MILLILITER", "5.00", "1", "L", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_TYPE_MISMATCH"));

        doPost(BASE, ingredientJson("Leite", null, UUID.randomUUID(), "MILLILITER", "5.00", "1", "L", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void createValidationReportsNestedFieldPaths() throws Exception {
        doPost(BASE, ingredientJson("Farinha", null, flours, "GRAM", "0", "1", "KG", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'price.packagePrice')]").exists());
    }

    @Test
    void identityIsNamePlusBrandIgnoringCaseAndABlankBrandCountsAsNone() throws Exception {
        createIngredient("Leite condensado", null, flours, "MILLILITER", "6.00", "395", "ML", null);

        doPost(BASE, ingredientJson("LEITE CONDENSADO", "  ", flours, "MILLILITER", "6.00", "395", "ML", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INGREDIENT_ALREADY_EXISTS"));

        doPost(BASE, ingredientJson("Leite condensado", "Moça", flours, "MILLILITER", "7.00", "395", "ML", null))
                .andExpect(status().isCreated());
    }

    @Test
    void aNewerPurchaseBecomesTheCurrentPriceAndYieldsTheVariation() throws Exception {
        UUID id = createIngredient("Farinha de trigo", null, flours, "GRAM", "10.00", "1", "KG", "2026-10-01");

        doPost(BASE + "/" + id + "/prices", purchaseJson("12.00", "1", "KG", "2026-10-20"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(0.012))
                .andExpect(jsonPath("$.lastPurchasedOn").value("2026-10-20"))
                .andExpect(jsonPath("$.variationPercent").value(20.0));
    }

    @Test
    void aBackdatedPurchaseOnlyEntersTheHistoryAndNeverRegressesTheCurrentPrice() throws Exception {
        UUID id = ingredientWithTwoPurchases();

        doPost(BASE + "/" + id + "/prices", purchaseJson("11.00", "1", "KG", "2026-10-10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(0.012))
                .andExpect(jsonPath("$.lastPurchasedOn").value("2026-10-20"))
                .andExpect(jsonPath("$.variationPercent").value(9.09));

        doGet(BASE + "/" + id + "/prices")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].purchasedOn").value("2026-10-20"))
                .andExpect(jsonPath("$[1].purchasedOn").value("2026-10-10"))
                .andExpect(jsonPath("$[2].purchasedOn").value("2026-10-01"));
    }

    @Test
    void twoPurchasesOnTheSameDayLetTheLatestRegisteredWin() throws Exception {
        UUID id = ingredientWithTwoPurchases();

        doPost(BASE + "/" + id + "/prices", purchaseJson("13.00", "1", "KG", "2026-10-20"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(0.013))
                .andExpect(jsonPath("$.variationPercent").value(8.33));
    }

    @Test
    void invalidPurchasesAreRejectedAndLeaveNoTrace() throws Exception {
        UUID id = createIngredient("Farinha de trigo", null, flours, "GRAM", "10.00", "1", "KG", "2026-10-01");

        doPost(BASE + "/" + id + "/prices", purchaseJson("12.00", "1", "KG", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PURCHASE_DATE_IN_FUTURE"));

        doPost(BASE + "/" + id + "/prices", purchaseJson("12.00", "1", "L", "2026-10-20"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_UNIT"));

        doGet(BASE + "/" + id + "/prices").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unknownIngredientIsNotFoundForPricesAndHistory() throws Exception {
        UUID unknown = UUID.randomUUID();
        doPost(BASE + "/" + unknown + "/prices", purchaseJson("12.00", "1","KG", "2026-10-20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
        doGet(BASE + "/" + unknown + "/prices")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
    }

    @Test
    void updateChangesTheDescriptiveFieldsAndKeepsPriceAndUnit() throws Exception {
        UUID id = createIngredient("Farinha de trigo", null, flours, "GRAM", "10.00", "1", "KG", "2026-10-01");
        UUID other = categories.save(new Category("Secos", CategoryType.INGREDIENT)).getId();

        doPut(BASE + "/" + id, """
                {"name":"Farinha de trigo especial","brandSupplier":"Dona Benta","categoryId":"%s"}""".formatted(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Farinha de trigo especial"))
                .andExpect(jsonPath("$.brandSupplier").value("Dona Benta"))
                .andExpect(jsonPath("$.category.name").value("Secos"))
                .andExpect(jsonPath("$.baseUnit").value("GRAM"))
                .andExpect(jsonPath("$.unitPrice").value(0.01));
    }

    @Test
    void updateAllowsTheOwnIdentityButRejectsAnotherIngredientsIdentity() throws Exception {
        UUID flour = createIngredient("Farinha de trigo", null, flours, "GRAM", "10.00", "1", "KG", null);
        createIngredient("Açúcar", null, flours, "GRAM", "5.00", "1", "KG", null);

        doPut(BASE + "/" + flour, """
                {"name":"farinha de trigo","categoryId":"%s"}""".formatted(flours))
                .andExpect(status().isOk());

        doPut(BASE + "/" + flour, """
                {"name":"AÇÚCAR","categoryId":"%s"}""".formatted(flours))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INGREDIENT_ALREADY_EXISTS"));
    }

    @Test
    void archivedIngredientsLeaveTheActiveListBlockTheirIdentityAndCanBeRestored() throws Exception {
        UUID id = createIngredient("Farinha de trigo", null, flours, "GRAM", "10.00", "1", "KG", null);

        doPost(BASE + "/" + id + "/archive", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        doPost(BASE + "/" + id + "/archive", "").andExpect(status().isOk());

        doGet(BASE + "?active=true").andExpect(jsonPath("$.length()").value(0));
        doGet(BASE + "?active=false").andExpect(jsonPath("$.length()").value(1));

        doPost(BASE, ingredientJson("Farinha de trigo", null, flours, "GRAM","10.00", "1", "KG", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHIVED_INGREDIENT_EXISTS"));

        doPost(BASE + "/" + id + "/restore", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void listFiltersSortsIgnoringCaseAndCarriesLastPurchaseAndVariationForEachRow() throws Exception {
        UUID dairy = categories.save(new Category("Laticinios", CategoryType.INGREDIENT)).getId();
        createIngredient("Leite condensado", "Moça", dairy, "MILLILITER", "6.00", "395", "ML", "2026-10-05");
        UUID flour = createIngredient("farinha de trigo", "Dona Benta", flours, "GRAM", "10.00", "1", "KG", "2025-10-01");
        createIngredient("Acucar refinado", null, flours, "GRAM", "5.00", "1", "KG", "2026-10-02");
        doPost(BASE + "/" + flour + "/prices", purchaseJson("12.00", "1", "KG", "2026-10-20"))
                .andExpect(status().isCreated());

        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Acucar refinado"))
                .andExpect(jsonPath("$[1].name").value("farinha de trigo"))
                .andExpect(jsonPath("$[2].name").value("Leite condensado"))
                .andExpect(jsonPath("$[0].variationPercent").doesNotExist())
                .andExpect(jsonPath("$[1].variationPercent").value(20.0))
                .andExpect(jsonPath("$[1].lastPurchasedOn").value("2026-10-20"))
                .andExpect(jsonPath("$[2].lastPurchasedOn").value("2026-10-05"));

        doGet(BASE + "?categoryId=" + dairy)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Leite condensado"));

        doGet(BASE + "?q=mo")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Leite condensado"));

        doGet(BASE + "?q=TRIGO")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("farinha de trigo"));
    }

    @Test
    void searchTreatsLikeWildcardsAsLiteralText() throws Exception {
        createIngredient("Farinha de trigo", null, flours, "GRAM", "10.00", "1", "KG", null);

        doGet(BASE + "?q=%25").andExpect(jsonPath("$.length()").value(0));
        doGet(BASE + "?q=_").andExpect(jsonPath("$.length()").value(0));
    }

    private UUID ingredientWithTwoPurchases() throws Exception {
        UUID id = createIngredient("Farinha de Trigo", null, flours, "GRAM", "10.00", "1", "KG", "2026-10-01");
        doPost(BASE + "/" + id + "/prices", purchaseJson("12.00", "1", "KG", "2026-10-20"))
                .andExpect(status().isCreated());
        return id;
    }

    private UUID createIngredient(String name, String brand, UUID categoryId, String baseUnit,
                                  String price, String quantity, String unit, String purchasedOn) throws Exception {
        String body = doPost(BASE, ingredientJson(name, brand, categoryId, baseUnit, price, quantity, unit, purchasedOn))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String ingredientJson(String name, String brand, UUID categoryId, String baseUnit,
                                         String price, String quantity, String unit, String purchasedOn) {
        return """
                {"name":"%s","brandSupplier":%s,"categoryId":"%s","baseUnit":"%s",
                "price":%s}""".formatted(name, quoted(brand), categoryId, baseUnit,
                purchaseJson(price, quantity, unit, purchasedOn));
    }

    private static String purchaseJson(String price, String quantity, String unit, String purchasedOn) {
        return """
                {"packagePrice":%s,"packageQuantity":%s,"unit":"%s","purchasedOn":%s}"""
                .formatted(price, quantity, unit, quoted(purchasedOn));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
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
