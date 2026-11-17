package dev.peluci.doceenccoreapi.order;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.support.DatabaseCleaner;
import dev.peluci.doceenccoreapi.support.TestAuthenticator;
import dev.peluci.doceenccoreapi.auth.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class OrderApiTestSupport {

    protected static final String BASE = "/api/v1/orders";
    protected static final String HEADER = TestAuthenticator.REQUIRED_HEADER;

    protected static final String BRIGADEIRO = "Cento de brigadeiro";
    protected static final String BROWNIE = "Brownie";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected CategoryRepository categories;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected DatabaseCleaner cleaner;

    protected Cookie session;
    protected UUID chocolate;
    protected UUID recipe;
    protected UUID brigadeiro;
    protected UUID brownie;
    protected UUID ana;
    protected UUID mercadinho;

    @BeforeEach
    void setUpFixture() throws Exception {
        cleaner.clean();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();

        UUID supplies = categories.save(new Category("Insumos", CategoryType.INGREDIENT)).getId();
        UUID sweets = categories.save(new Category("Doces", CategoryType.RECIPE)).getId();
        chocolate = createIngredient("Chocolate", supplies);
        recipe = createRecipe("Brigadeiro", sweets, chocolate);
        brigadeiro = createProduct(BRIGADEIRO, "15.00", "11.54");
        brownie = createProduct(BROWNIE, "8.50", null);

        ana = createCustomer("Ana", "RETAIL", null, "Rua A, 10");
        mercadinho = createCustomer("Mercadinho", "WHOLESALE", "Mercadinho Doce", null);
    }

    protected UUID createOrder(UUID customerId, String deliveryDate, String... items) throws Exception {
        String body = doPost(BASE, pickup(customerId, deliveryDate, items))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected static String pickup(UUID customerId, String deliveryDate, String... items) {
        return orderJson(customerId, "PICKUP", null, deliveryDate, null, items);
    }

    protected static String orderJson(UUID customerId, String fulfillmentType, String address, String deliveryDate,
                                      String notes, String... items) {
        return """
                {"customerId":"%s","fulfillmentType":"%s","deliveryAddress":%s,"deliveryDate":%s,
                "notes":%s,"items":[%s]}""".formatted(customerId, fulfillmentType, quoted(address),
                quoted(deliveryDate), quoted(notes), String.join(",", items));
    }

    protected static String item(UUID productId, int quantity) {
        return """
                {"productId":"%s","quantity":%d}""".formatted(productId, quantity);
    }

    protected static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    protected static String chocolatePrice(String packagePrice) {
        return """
                {"packagePrice":%s,"packageQuantity":1,"unit":"KG","purchasedOn":"2026-10-20"}"""
                .formatted(packagePrice);
    }

    protected void archive(String resource, UUID id) throws Exception {
        doPost("/api/v1/" + resource + "/" + id + "/archive", "").andExpect(status().isOk());
    }

    protected UUID createCustomer(String name, String type, String storeName, String address) throws Exception {
        String body = doPost("/api/v1/customers", """
                {"name":"%s","type":"%s","phone":null,"address":%s,"storeName":%s,"notes":null}"""
                .formatted(name, type, quoted(address), quoted(storeName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected UUID createIngredient(String name, UUID categoryId) throws Exception {
        String body = doPost("/api/v1/ingredients", """
                {"name":"%s","brandSupplier":null,"categoryId":"%s","baseUnit":"GRAM",
                "price":{"packagePrice":68.00,"packageQuantity":1,"unit":"KG","purchasedOn":"2026-10-01"}}"""
                .formatted(name, categoryId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected UUID createRecipe(String name, UUID categoryId, UUID ingredientId) throws Exception {
        String body = doPost("/api/v1/recipes", """
                {"name":"%s","categoryId":"%s","yieldQuantity":100,"yieldUnit":"UNIT",
                "items":[{"ingredientId":"%s","quantity":1000}]}"""
                .formatted(name, categoryId, ingredientId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected UUID createProduct(String name, String retail, String wholesale) throws Exception {
        String body = doPost("/api/v1/products", productJson(name, retail, wholesale))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected void updateProduct(UUID id, String name, String retail, String wholesale) throws Exception {
        doPut("/api/v1/products/" + id, productJson(name, retail, wholesale)).andExpect(status().isOk());
    }

    protected String productJson(String name, String retail, String wholesale) {
        return """
                {"name":"%s","recipeId":"%s","recipeQuantity":1,"retailPrice":%s,"wholesalePrice":%s,"items":[]}"""
                .formatted(name, recipe, retail == null ? "null" : retail, wholesale == null ? "null" : wholesale);
    }

    protected ResultActions doGet(String url) throws Exception {
        return mockMvc.perform(get(url).cookie(session));
    }

    protected ResultActions doPost(String url, String body) throws Exception {
        return mockMvc.perform(post(url).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions doPut(String url, String body) throws Exception {
        return mockMvc.perform(put(url).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}