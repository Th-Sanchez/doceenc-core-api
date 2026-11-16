package dev.peluci.doceenccoreapi.order;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.product.Product;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class OrderListIntegrationTest {

    private static final String BASE = "/api/v1/orders";
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

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Cookie session;
    private UUID ana;
    private UUID mercadinho;
    private UUID brigadeiro;

    @BeforeEach
    void setUp() throws Exception {
        cleaner.clean();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();

        UUID supplies = categories.save(new Category("Insumos", CategoryType.INGREDIENT)).getId();
        UUID sweets = categories.save(new Category("Doces", CategoryType.RECIPE)).getId();
        UUID chocolate = createIngredient("Chocolate", supplies);
        UUID recipe = createRecipe("Brigadeiro", sweets, chocolate);
        brigadeiro = createProduct("Cento de brigadeiro", recipe, "15.00", "11.54");

        ana = createCustomer("Ana", "RETAIL", null, "Rua A, 10");
        mercadinho = createCustomer("Mercadinho", "WHOLESALE", "Mercadinho Doce", null);
    }

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void anEmptyListStillHasThePageEnvelope() throws Exception {
        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void sortsByDeliveryDateWithoutDateLastAndTiesByOrderNumber() throws Exception {
        UUID late = createOrder(ana, "2026-12-10");
        UUID first = createOrder(ana, "2026-12-05");
        UUID withoutDate = createOrder(ana, null);
        UUID tie = createOrder(ana, "2026-12-05");

        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()))
                .andExpect(jsonPath("$.items[1].id").value(tie.toString()))
                .andExpect(jsonPath("$.items[2].id").value(late.toString()))
                .andExpect(jsonPath("$.items[3].id").value(withoutDate.toString()));
    }

    @Test
    void summaryCarriesCustomerItemCountAndTheLiveTotalForPendingOrders() throws Exception {
        createOrder(mercadinho, "2026-12-05", item(brigadeiro, 3));

        doGet(BASE)
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].customer.name").value("Mercadinho"))
                .andExpect(jsonPath("$.items[0].customer.storeName").value("Mercadinho Doce"))
                .andExpect(jsonPath("$.items[0].itemCount").value(1))
                .andExpect(jsonPath("$.items[0].totalAmount").value(34.62));
    }

    @Test
    void filtersByOneOrManyStatuses() throws Exception {
        createOrder(ana, "2026-12-05", item(brigadeiro, 1));
        UUID confirmed = createOrder(ana, "2026-12-06", item(brigadeiro, 1));
        UUID cancelled = createOrder(ana, "2026-12-07", item(brigadeiro, 1));
        doPost(BASE + "/" + confirmed + "/confirm", "").andExpect(status().isOk());
        doPost(BASE + "/" + cancelled + "/cancel", "").andExpect(status().isOk());

        doGet(BASE + "?status=CONFIRMED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].totalAmount").value(15.0));

        doGet(BASE + "?status=PENDING&status=CONFIRMED")
                .andExpect(jsonPath("$.totalElements").value(2));

        doGet(BASE + "?status=CANCELLED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].totalAmount").doesNotExist());
    }

    @Test
    void deliveryDateFiltersAreInclusiveAndExcludeOrdersWithoutADate() throws Exception {
        createOrder(ana, "2026-12-04");
        createOrder(ana, "2026-12-05");
        createOrder(ana, "2026-12-07");
        createOrder(ana, "2026-12-08");
        createOrder(ana, null);

        doGet(BASE + "?deliveryFrom=2026-12-05&deliveryTo=2026-12-07")
                .andExpect(jsonPath("$.totalElements").value(2));
        doGet(BASE + "?deliveryFrom=2026-12-07")
                .andExpect(jsonPath("$.totalElements").value(2));
        doGet(BASE + "?deliveryTo=2026-12-05")
                .andExpect(jsonPath("$.totalElements").value(2));
        doGet(BASE + "?deliveryFrom=2026-12-05&deliveryTo=2026-12-05")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtersByCustomer() throws Exception {
        createOrder(ana, "2026-12-05");
        createOrder(mercadinho, "2026-12-05");
        createOrder(mercadinho, "2026-12-06");

        doGet(BASE + "?customerId=" + mercadinho)
                .andExpect(jsonPath("$.totalElements").value(2));
        doGet(BASE + "?customerId=" + UUID.randomUUID())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginatesOnTheServer() throws Exception {
        for (int day = 1; day <= 5; day++) {
            createOrder(ana, "2026-12-0" + day);
        }

        doGet(BASE + "?size=2&page=0")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].deliveryDate").value("2026-12-01"));
        doGet(BASE + "?size=2&page=2")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[0].deliveryDate").value("2026-12-05"));
    }

    @Test
    void invalidQueriesAreBadRequests() throws Exception {
        doGet(BASE + "?deliveryFrom=2026-12-12&deliveryTo=2026-12-05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        doGet(BASE + "?size=101").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        doGet(BASE + "?size=0").andExpect(status().isBadRequest());
        doGet(BASE + "?page=-1").andExpect(status().isBadRequest());
        doGet(BASE + "?status=NOPE").andExpect(status().isBadRequest());
        doGet(BASE + "?deliveryFrom=05/12/2026").andExpect(status().isBadRequest());
    }

    @Test
    void listingPendingOrdersDoesNotIssueMoreQueriesWhenThereAreMoreOrders() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            for (int i = 1; i <= 3; i++) {
                createOrder(ana, "2026-12-0" + i, item(brigadeiro, i));
            }
            long withThree = statementsForList(statistics, 3);

            for (int i = 4; i <= 9; i++) {
                createOrder(i % 2 == 0 ? ana : mercadinho, "2026-12-0" + i, item(brigadeiro, i));
            }
            long withNine = statementsForList(statistics, 9);

            assertThat(withNine).isEqualTo(withThree);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void listingConfirmedOrdersNeverLoadsProducts() throws Exception {
        for (int day = 1; day <= 3; day++) {
            UUID id = createOrder(ana, "2026-12-0" + day, item(brigadeiro, day));
            doPost(BASE + "/" + id + "/confirm", "").andExpect(status().isOk());
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            doGet(BASE + "?status=CONFIRMED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(3));

            assertThat(statistics.getEntityStatistics(Product.class.getName()).getLoadCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private long statementsForList(Statistics statistics, int expectedOrders) throws Exception {
        doGet(BASE).andExpect(status().isOk());
        statistics.clear();
        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(expectedOrders));
        return statistics.getPrepareStatementCount();
    }

    private UUID createOrder(UUID customerId, String deliveryDate, String... items) throws Exception {
        String body = doPost(BASE, """
                {"customerId":"%s","fulfillmentType":"PICKUP","deliveryAddress":null,"deliveryDate":%s,
                "notes":null,"items":[%s]}""".formatted(customerId,
                deliveryDate == null ? "null" : "\"" + deliveryDate + "\"", String.join(",", items)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String item(UUID productId, int quantity) {
        return """
                {"productId":"%s","quantity":%d}""".formatted(productId, quantity);
    }

    private UUID createCustomer(String name, String type, String storeName, String address) throws Exception {
        String body = doPost("/api/v1/customers", """
                {"name":"%s","type":"%s","phone":null,"address":%s,"storeName":%s,"notes":null}"""
                .formatted(name, type, address == null ? "null" : "\"" + address + "\"",
                        storeName == null ? "null" : "\"" + storeName + "\""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID createIngredient(String name, UUID categoryId) throws Exception {
        String body = doPost("/api/v1/ingredients", """
                {"name":"%s","brandSupplier":null,"categoryId":"%s","baseUnit":"GRAM",
                "price":{"packagePrice":68.00,"packageQuantity":1,"unit":"KG","purchasedOn":"2026-10-01"}}"""
                .formatted(name, categoryId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID createRecipe(String name, UUID categoryId, UUID ingredientId) throws Exception {
        String body = doPost("/api/v1/recipes", """
                {"name":"%s","categoryId":"%s","yieldQuantity":100,"yieldUnit":"UNIT",
                "items":[{"ingredientId":"%s","quantity":1000}]}"""
                .formatted(name, categoryId, ingredientId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID createProduct(String name, UUID recipeId, String retail, String wholesale) throws Exception {
        String body = doPost("/api/v1/products", """
                {"name":"%s","recipeId":"%s","recipeQuantity":1,"retailPrice":%s,"wholesalePrice":%s,"items":[]}"""
                .formatted(name, recipeId, retail, wholesale))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
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