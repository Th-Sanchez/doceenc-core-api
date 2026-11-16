package dev.peluci.doceenccoreapi.order;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.support.DatabaseCleaner;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.TestAuthenticator;
import dev.peluci.doceenccoreapi.auth.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class OrderIntegrationTest {

    private static final String BASE = "/api/v1/orders";
    private static final String HEADER = TestAuthenticator.REQUIRED_HEADER;

    private static final String BRIGADEIRO = "Cento de brigadeiro";
    private static final String BROWNIE = "Brownie";

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
    private UUID chocolate;
    private UUID recipe;
    private UUID brigadeiro;
    private UUID brownie;
    private UUID ana;
    private UUID mercadinho;

    /**
     * One recipe costs 68.00 for 100 units, so every product built on it has a unit cost of 0.68.
     * brigadeiro: retail 15.00 / wholesale 11.54. brownie: retail 8.50 only (no wholesale price).
     * ana: RETAIL with an address. mercadinho: WHOLESALE without an address.
     */
    @BeforeEach
    void setUp() throws Exception {
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

    // ---------- create and read ----------

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/simulation").header(HEADER, "test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsAPendingOrderPricedLiveInTheCustomersChannel() throws Exception {
        doPost(BASE, orderJson(ana, "PICKUP", null, "2026-12-05", "Sem granulado",
                item(brigadeiro, 3), item(brownie, 2)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderNumber").isNumber())
                .andExpect(jsonPath("$.customer.name").value("Ana"))
                .andExpect(jsonPath("$.customer.type").value("RETAIL"))
                .andExpect(jsonPath("$.priceChannel").value("RETAIL"))
                .andExpect(jsonPath("$.fulfillmentType").value("PICKUP"))
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.deliveryDate").value("2026-12-05"))
                .andExpect(jsonPath("$.notes").value("Sem granulado"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value(BROWNIE))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(8.5))
                .andExpect(jsonPath("$.items[0].unitCost").value(0.68))
                .andExpect(jsonPath("$.items[0].subtotal").value(17.0))
                .andExpect(jsonPath("$.items[1].productName").value(BRIGADEIRO))
                .andExpect(jsonPath("$.items[1].subtotal").value(45.0))
                .andExpect(jsonPath("$.items[1].issue").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").value(62.0))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void aWholesaleCustomerIsPricedWithTheWholesalePrice() throws Exception {
        doPost(BASE, pickup(mercadinho, "2026-12-05", item(brigadeiro, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceChannel").value("WHOLESALE"))
                .andExpect(jsonPath("$.customer.storeName").value("Mercadinho Doce"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(11.54))
                .andExpect(jsonPath("$.totalAmount").value(34.62));
    }

    @Test
    void anOrderWithoutItemsIsAValidDraft() throws Exception {
        doPost(BASE, pickup(ana, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0.0));
    }

    @Test
    void getReturnsTheOrderAndUnknownOrdersAreNotFound() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 1));

        doGet(BASE + "/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.totalAmount").value(15.0));

        UUID unknown = UUID.randomUUID();
        doGet(BASE + "/" + unknown)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        doPut(BASE + "/" + unknown, pickup(ana, null)).andExpect(status().isNotFound());
        doPost(BASE + "/" + unknown + "/confirm", "").andExpect(status().isNotFound());
        doPost(BASE + "/" + unknown + "/deliver", "").andExpect(status().isNotFound());
        doPost(BASE + "/" + unknown + "/cancel", "").andExpect(status().isNotFound());
    }

    @Test
    void orderNumbersAreSequential() throws Exception {
        doPost(BASE, pickup(ana, null)).andExpect(jsonPath("$.orderNumber").value(1));
        doPost(BASE, pickup(ana, null)).andExpect(jsonPath("$.orderNumber").value(2));
        doPost(BASE, pickup(mercadinho, null)).andExpect(jsonPath("$.orderNumber").value(3));
    }

    @Test
    void createValidationReportsFieldPaths() throws Exception {
        doPost(BASE, """
                {"fulfillmentType":"PICKUP","items":[]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customerId')]").exists());

        doPost(BASE, pickup(ana, null, item(brigadeiro, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')]").exists());

        doPost(BASE, """
                {"customerId":"%s","fulfillmentType":"PICKUP"}""".formatted(ana))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items')]").exists());

        doPost(BASE, """
                {"customerId":"%s","fulfillmentType":"TELEPORT","items":[]}""".formatted(ana))
                .andExpect(status().isBadRequest());

        assertNoOrders();
    }

    @Test
    void unknownArchivedOrUnpricedReferencesAreRejectedAndNothingIsSaved() throws Exception {
        doPost(BASE, pickup(UUID.randomUUID(), null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        doPost(BASE, pickup(ana, null, item(UUID.randomUUID(), 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        doPost(BASE, pickup(ana, null, item(brigadeiro, 1), item(brigadeiro, 2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ORDER_ITEM"));

        doPost(BASE, pickup(mercadinho, null, item(brownie, 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRODUCT_PRICE_UNAVAILABLE"));

        archive("products", brownie);
        doPost(BASE, pickup(ana, null, item(brownie, 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_PRODUCT_NOT_ALLOWED"));

        archive("customers", ana);
        doPost(BASE, pickup(ana, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_CUSTOMER_NOT_ALLOWED"));

        assertNoOrders();
    }

    // ---------- delivery address ----------

    @Test
    void deliveryUsesTheCustomersAddressUnlessOneIsInformed() throws Exception {
        doPost(BASE, orderJson(ana, "DELIVERY", null, "2026-12-05", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryAddress").value("Rua A, 10"));

        doPost(BASE, orderJson(ana, "DELIVERY", "   ", "2026-12-05", null))
                .andExpect(jsonPath("$.deliveryAddress").value("Rua A, 10"));

        doPost(BASE, orderJson(ana, "DELIVERY", "Rua B, 20", "2026-12-05", null))
                .andExpect(jsonPath("$.deliveryAddress").value("Rua B, 20"));
    }

    @Test
    void pickupKeepsNoAddressEvenWhenOneIsSent() throws Exception {
        doPost(BASE, orderJson(ana, "PICKUP", "Rua B, 20", "2026-12-05", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist());
    }

    @Test
    void deliveryWithoutAnyAddressIsRejected() throws Exception {
        doPost(BASE, orderJson(mercadinho, "DELIVERY", null, "2026-12-05", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DELIVERY_ADDRESS_REQUIRED"));

        doPost(BASE, orderJson(mercadinho, "DELIVERY", "Rua do Comércio, 5", "2026-12-05", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryAddress").value("Rua do Comércio, 5"));
    }

    // ---------- update ----------

    @Test
    void updateReplacesCustomerDeliveryItemsAndNotes() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 3), item(brownie, 1));

        doPut(BASE + "/" + id, orderJson(mercadinho, "DELIVERY", "Rua do Comércio, 5", "2026-12-08",
                "Entregar de manhã", item(brigadeiro, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("Mercadinho"))
                .andExpect(jsonPath("$.priceChannel").value("WHOLESALE"))
                .andExpect(jsonPath("$.fulfillmentType").value("DELIVERY"))
                .andExpect(jsonPath("$.deliveryAddress").value("Rua do Comércio, 5"))
                .andExpect(jsonPath("$.deliveryDate").value("2026-12-08"))
                .andExpect(jsonPath("$.notes").value("Entregar de manhã"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.totalAmount").value(57.7));
    }

    @Test
    void updateKeepsProductsThatStayAndChangesOnlyTheirQuantity() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 3), item(brownie, 1));

        doPut(BASE + "/" + id, pickup(ana, "2026-12-05", item(brigadeiro, 4), item(brownie, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].quantity").value(4))
                .andExpect(jsonPath("$.totalAmount").value(68.5));

        doPut(BASE + "/" + id, pickup(ana, "2026-12-05", item(brigadeiro, 4), item(brownie, 1)))
                .andExpect(status().isOk());
    }

    @Test
    void changingOnlyAQuantityStillTouchesUpdatedAt() throws Exception {
        String created = doPost(BASE, pickup(ana, "2026-12-05", item(brigadeiro, 3)))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));
        Instant before = Instant.parse(JsonPath.read(created, "$.updatedAt"));

        String updated = doPut(BASE + "/" + id, pickup(ana, "2026-12-05", item(brigadeiro, 4)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(JsonPath.read(updated, "$.updatedAt"))).isAfter(before);
    }

    @Test
    void onlyPendingOrdersCanBeEditedAndThatCheckComesFirst() throws Exception {
        UUID confirmed = createOrder(ana, "2026-12-05", item(brigadeiro, 1));
        doPost(BASE + "/" + confirmed + "/confirm", "").andExpect(status().isOk());

        UUID cancelled = createOrder(ana, "2026-12-06", item(brigadeiro, 1));
        doPost(BASE + "/" + cancelled + "/cancel", "").andExpect(status().isOk());

        for (UUID id : List.of(confirmed, cancelled)) {
            doPut(BASE + "/" + id, pickup(ana, "2026-12-09", item(brigadeiro, 9)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));
        }

        doPut(BASE + "/" + confirmed, pickup(UUID.randomUUID(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));

        doGet(BASE + "/" + confirmed).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void updateRejectsAnItemThatWasArchivedAfterItWasAdded() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 1), item(brownie, 1));
        archive("products", brownie);

        doPut(BASE + "/" + id, pickup(ana, "2026-12-05", item(brigadeiro, 1), item(brownie, 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_PRODUCT_NOT_ALLOWED"));

        doPut(BASE + "/" + id, pickup(ana, "2026-12-05", item(brigadeiro, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    // ---------- live pricing while PENDING ----------

    @Test
    void aPendingOrderFollowsTheProductPricesAndCosts() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 2));
        doGet(BASE + "/" + id).andExpect(jsonPath("$.totalAmount").value(30.0));

        updateProduct(brigadeiro, BRIGADEIRO, "20.00", "11.54");
        doPost("/api/v1/ingredients/" + chocolate + "/prices", chocolatePrice("136.00"))
                .andExpect(status().isCreated());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.items[0].unitPrice").value(20.0))
                .andExpect(jsonPath("$.items[0].unitCost").value(1.36))
                .andExpect(jsonPath("$.totalAmount").value(40.0));
    }

    @Test
    void aPendingOrderFlagsLinesThatCanNoLongerBePricedAndLeavesThemOutOfTheTotal() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 2), item(brownie, 1));

        archive("products", brownie);
        doGet(BASE + "/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value(BROWNIE))
                .andExpect(jsonPath("$.items[0].issue").value("ARCHIVED"))
                .andExpect(jsonPath("$.items[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.items[0].subtotal").doesNotExist())
                .andExpect(jsonPath("$.items[1].issue").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").value(30.0));

        updateProduct(brigadeiro, BRIGADEIRO, null, "11.54");
        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.items[1].issue").value("NO_PRICE"))
                .andExpect(jsonPath("$.items[1].unitCost").value(0.68))
                .andExpect(jsonPath("$.totalAmount").value(0.0));
    }

    @Test
    void changingTheCustomerTypeRepricesPendingOrdersButNotConfirmedOnes() throws Exception {
        UUID pending = createOrder(ana, "2026-12-05", item(brigadeiro, 2));
        UUID confirmed = createOrder(ana, "2026-12-06", item(brigadeiro, 2));
        doPost(BASE + "/" + confirmed + "/confirm", "").andExpect(status().isOk());

        doPut("/api/v1/customers/" + ana, """
                {"name":"Ana","type":"WHOLESALE","phone":null,"address":"Rua A, 10",
                "storeName":"Loja da Ana","notes":null}""")
                .andExpect(status().isOk());

        doGet(BASE + "/" + pending)
                .andExpect(jsonPath("$.customer.type").value("WHOLESALE"))
                .andExpect(jsonPath("$.priceChannel").value("WHOLESALE"))
                .andExpect(jsonPath("$.totalAmount").value(23.08));

        doGet(BASE + "/" + confirmed)
                .andExpect(jsonPath("$.customer.type").value("WHOLESALE"))
                .andExpect(jsonPath("$.priceChannel").value("RETAIL"))
                .andExpect(jsonPath("$.totalAmount").value(30.0));
    }

    // ---------- confirm ----------

    @Test
    void confirmFreezesTheSnapshot() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 3), item(brownie, 2));

        doPost(BASE + "/" + id + "/confirm", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isString())
                .andExpect(jsonPath("$.priceChannel").value("RETAIL"))
                .andExpect(jsonPath("$.totalAmount").value(62.0))
                .andExpect(jsonPath("$.items[0].productName").value(BROWNIE))
                .andExpect(jsonPath("$.items[0].unitPrice").value(8.5))
                .andExpect(jsonPath("$.items[0].unitCost").value(0.68))
                .andExpect(jsonPath("$.items[1].subtotal").value(45.0))
                .andExpect(jsonPath("$.items[1].issue").doesNotExist());
    }

    @Test
    void aConfirmedOrderIsFrozenWhileAPendingOneFollowsTheProduct() throws Exception {
        UUID pending = createOrder(ana, "2026-12-05", item(brigadeiro, 2));
        UUID confirmed = createOrder(ana, "2026-12-06", item(brigadeiro, 2));
        doPost(BASE + "/" + confirmed + "/confirm", "").andExpect(status().isOk());

        updateProduct(brigadeiro, "Cento de brigadeiro premium", "20.00", "15.00");
        doPost("/api/v1/ingredients/" + chocolate + "/prices", chocolatePrice("136.00"))
                .andExpect(status().isCreated());

        doGet(BASE + "/" + confirmed)
                .andExpect(jsonPath("$.totalAmount").value(30.0))
                .andExpect(jsonPath("$.items[0].productName").value(BRIGADEIRO))
                .andExpect(jsonPath("$.items[0].unitPrice").value(15.0))
                .andExpect(jsonPath("$.items[0].unitCost").value(0.68))
                .andExpect(jsonPath("$.items[0].subtotal").value(30.0));

        doGet(BASE + "/" + pending)
                .andExpect(jsonPath("$.totalAmount").value(40.0))
                .andExpect(jsonPath("$.items[0].productName").value("Cento de brigadeiro premium"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(20.0))
                .andExpect(jsonPath("$.items[0].unitCost").value(1.36));

        archive("products", brigadeiro);
        doGet(BASE + "/" + confirmed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(30.0))
                .andExpect(jsonPath("$.items[0].issue").doesNotExist());
        doGet(BASE + "?status=CONFIRMED")
                .andExpect(jsonPath("$.items[0].totalAmount").value(30.0));
    }

    @Test
    void confirmNeedsItemsAndADeliveryDateInThatOrder() throws Exception {
        UUID empty = createOrder(ana, null);
        doPost(BASE + "/" + empty + "/confirm", "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORDER_ITEMS_REQUIRED"));

        UUID withoutDate = createOrder(ana, null, item(brigadeiro, 1));
        doPost(BASE + "/" + withoutDate + "/confirm", "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DELIVERY_DATE_REQUIRED"));
        doGet(BASE + "/" + withoutDate).andExpect(jsonPath("$.status").value("PENDING"));

        doPut(BASE + "/" + withoutDate, pickup(ana, "2026-12-05", item(brigadeiro, 1))).andExpect(status().isOk());
        doPost(BASE + "/" + withoutDate + "/confirm", "").andExpect(status().isOk());
    }

    @Test
    void confirmRejectsUnpricedOrArchivedProductsAndLeavesTheOrderPending() throws Exception {
        UUID unpriced = createOrder(ana, "2026-12-05", item(brigadeiro, 1));
        updateProduct(brigadeiro, BRIGADEIRO, null, "11.54");
        doPost(BASE + "/" + unpriced + "/confirm", "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRODUCT_PRICE_UNAVAILABLE"));

        UUID withArchivedProduct = createOrder(ana, "2026-12-05", item(brownie, 1));
        archive("products", brownie);
        doPost(BASE + "/" + withArchivedProduct + "/confirm", "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_PRODUCT_NOT_ALLOWED"));

        for (UUID id : List.of(unpriced, withArchivedProduct)) {
            doGet(BASE + "/" + id).andExpect(jsonPath("$.status").value("PENDING"));
        }
    }

    @Test
    void confirmRejectsAnArchivedCustomer() throws Exception {
        UUID id = createOrder(mercadinho, "2026-12-05", item(brigadeiro, 1));
        archive("customers", mercadinho);

        doPost(BASE + "/" + id + "/confirm", "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_CUSTOMER_NOT_ALLOWED"));
        doGet(BASE + "/" + id).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void confirmingTwiceIsAnInvalidTransition() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 1));
        doPost(BASE + "/" + id + "/confirm", "").andExpect(status().isOk());

        doPost(BASE + "/" + id + "/confirm", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
    }

    @Test
    void aDoubleClickOnConfirmConfirmsOnlyOnce() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 2));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return doPost(BASE + "/" + id + "/confirm", "").andReturn();
                }));
            }
            ready.await();
            go.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> call : calls) {
                MvcResult result = call.get(30, TimeUnit.SECONDS);
                statuses.add(result.getResponse().getStatus());
                if (result.getResponse().getStatus() == 409) {
                    String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
                    assertThat(code).isIn(Set.of("INVALID_ORDER_TRANSITION", "CONCURRENT_MODIFICATION"));
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(30.0));
    }

    // ---------- deliver and cancel ----------

    @Test
    void deliverOnlyWorksFromConfirmed() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 1));

        doPost(BASE + "/" + id + "/deliver", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));

        doPost(BASE + "/" + id + "/confirm", "").andExpect(status().isOk());
        doPost(BASE + "/" + id + "/deliver", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isString())
                .andExpect(jsonPath("$.totalAmount").value(15.0));

        doPost(BASE + "/" + id + "/deliver", "").andExpect(status().isConflict());
        doPost(BASE + "/" + id + "/cancel", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
        doPut(BASE + "/" + id, pickup(ana, "2026-12-05"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));
    }

    @Test
    void cancellingAPendingOrderFreezesNothing() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 2));

        doPost(BASE + "/" + id + "/cancel", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isString())
                .andExpect(jsonPath("$.confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").doesNotExist())
                .andExpect(jsonPath("$.items[0].productName").value(BRIGADEIRO))
                .andExpect(jsonPath("$.items[0].unitPrice").doesNotExist());

        doGet(BASE + "?status=CANCELLED")
                .andExpect(jsonPath("$.items[0].totalAmount").doesNotExist());
    }

    @Test
    void cancellingAConfirmedOrderKeepsItsSnapshot() throws Exception {
        UUID id = createOrder(ana, "2026-12-05", item(brigadeiro, 2));
        doPost(BASE + "/" + id + "/confirm", "").andExpect(status().isOk());

        doPost(BASE + "/" + id + "/cancel", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.confirmedAt").isString())
                .andExpect(jsonPath("$.cancelledAt").isString())
                .andExpect(jsonPath("$.totalAmount").value(30.0))
                .andExpect(jsonPath("$.items[0].unitPrice").value(15.0));

        doPost(BASE + "/" + id + "/cancel", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
        doPost(BASE + "/" + id + "/confirm", "").andExpect(status().isConflict());
    }

    @Test
    void deliverAndCancelStillWorkForAnArchivedCustomer() throws Exception {
        UUID toDeliver = createOrder(ana, "2026-12-05", item(brigadeiro, 1));
        UUID toCancel = createOrder(ana, "2026-12-06", item(brigadeiro, 1));
        doPost(BASE + "/" + toDeliver + "/confirm", "").andExpect(status().isOk());
        doPost(BASE + "/" + toCancel + "/confirm", "").andExpect(status().isOk());
        archive("customers", ana);

        doPost(BASE + "/" + toDeliver + "/deliver", "").andExpect(status().isOk());
        doPost(BASE + "/" + toCancel + "/cancel", "").andExpect(status().isOk());
    }

    // ---------- simulation ----------

    @Test
    void simulationPricesTheLinesInTheCustomersChannelWithoutSavingAnything() throws Exception {
        doPost(BASE + "/simulation", simulationJson(ana, item(brigadeiro, 3), item(brownie, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceChannel").value("RETAIL"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value(BRIGADEIRO))
                .andExpect(jsonPath("$.items[0].unitPrice").value(15.0))
                .andExpect(jsonPath("$.items[0].unitCost").value(0.68))
                .andExpect(jsonPath("$.items[0].subtotal").value(45.0))
                .andExpect(jsonPath("$.items[0].issue").doesNotExist())
                .andExpect(jsonPath("$.items[1].productName").value(BROWNIE))
                .andExpect(jsonPath("$.items[1].subtotal").value(17.0))
                .andExpect(jsonPath("$.totalAmount").value(62.0));

        assertNoOrders();
    }

    @Test
    void simulationMarksALineWithoutPriceInTheChannelAndLeavesItOutOfTheTotal() throws Exception {
        doPost(BASE + "/simulation", simulationJson(mercadinho, item(brigadeiro, 3), item(brownie, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceChannel").value("WHOLESALE"))
                .andExpect(jsonPath("$.items[0].subtotal").value(34.62))
                .andExpect(jsonPath("$.items[1].issue").value("NO_PRICE"))
                .andExpect(jsonPath("$.items[1].productName").value(BROWNIE))
                .andExpect(jsonPath("$.items[1].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.items[1].unitCost").value(0.68))
                .andExpect(jsonPath("$.items[1].subtotal").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").value(34.62));
    }

    @Test
    void simulationReportsUnknownArchivedAndRepeatedProductsOnTheirLines() throws Exception {
        archive("products", brownie);
        UUID unknown = UUID.randomUUID();

        doPost(BASE + "/simulation", simulationJson(ana,
                item(brigadeiro, 1), item(brigadeiro, 5), item(brownie, 1), item(unknown, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].issue").doesNotExist())
                .andExpect(jsonPath("$.items[0].subtotal").value(15.0))
                .andExpect(jsonPath("$.items[1].issue").value("DUPLICATE"))
                .andExpect(jsonPath("$.items[1].quantity").value(5))
                .andExpect(jsonPath("$.items[1].subtotal").doesNotExist())
                .andExpect(jsonPath("$.items[2].issue").value("ARCHIVED"))
                .andExpect(jsonPath("$.items[3].issue").value("NOT_FOUND"))
                .andExpect(jsonPath("$.items[3].productId").value(unknown.toString()))
                .andExpect(jsonPath("$.items[3].productName").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").value(15.0));
    }

    @Test
    void simulationWithNoItemsTotalsZero() throws Exception {
        doPost(BASE + "/simulation", simulationJson(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0.0));
    }

    @Test
    void simulationOnlyFailsForTheCustomerOrAMalformedBody() throws Exception {
        doPost(BASE + "/simulation", simulationJson(UUID.randomUUID()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        archive("customers", mercadinho);
        doPost(BASE + "/simulation", simulationJson(mercadinho))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ARCHIVED_CUSTOMER_NOT_ALLOWED"));

        doPost(BASE + "/simulation", simulationJson(ana, item(brigadeiro, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')]").exists());
    }

    // ---------- helpers ----------

    private void assertNoOrders() throws Exception {
        doGet(BASE).andExpect(jsonPath("$.totalElements").value(0));
    }

    private UUID createOrder(UUID customerId, String deliveryDate, String... items) throws Exception {
        String body = doPost(BASE, pickup(customerId, deliveryDate, items))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String pickup(UUID customerId, String deliveryDate, String... items) {
        return orderJson(customerId, "PICKUP", null, deliveryDate, null, items);
    }

    private static String orderJson(UUID customerId, String fulfillmentType, String address, String deliveryDate,
                                    String notes, String... items) {
        return """
                {"customerId":"%s","fulfillmentType":"%s","deliveryAddress":%s,"deliveryDate":%s,
                "notes":%s,"items":[%s]}""".formatted(customerId, fulfillmentType, quoted(address),
                quoted(deliveryDate), quoted(notes), String.join(",", items));
    }

    private static String simulationJson(UUID customerId, String... items) {
        return """
                {"customerId":"%s","items":[%s]}""".formatted(customerId, String.join(",", items));
    }

    private static String item(UUID productId, int quantity) {
        return """
                {"productId":"%s","quantity":%d}""".formatted(productId, quantity);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String chocolatePrice(String packagePrice) {
        return """
                {"packagePrice":%s,"packageQuantity":1,"unit":"KG","purchasedOn":"2026-10-20"}"""
                .formatted(packagePrice);
    }

    private void archive(String resource, UUID id) throws Exception {
        doPost("/api/v1/" + resource + "/" + id + "/archive", "").andExpect(status().isOk());
    }

    private UUID createCustomer(String name, String type, String storeName, String address) throws Exception {
        String body = doPost("/api/v1/customers", """
                {"name":"%s","type":"%s","phone":null,"address":%s,"storeName":%s,"notes":null}"""
                .formatted(name, type, quoted(address), quoted(storeName)))
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

    private UUID createProduct(String name, String retail, String wholesale) throws Exception {
        String body = doPost("/api/v1/products", productJson(name, retail, wholesale))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private void updateProduct(UUID id, String name, String retail, String wholesale) throws Exception {
        doPut("/api/v1/products/" + id, productJson(name, retail, wholesale)).andExpect(status().isOk());
    }

    private String productJson(String name, String retail, String wholesale) {
        return """
                {"name":"%s","recipeId":"%s","recipeQuantity":1,"retailPrice":%s,"wholesalePrice":%s,"items":[]}"""
                .formatted(name, recipe, retail == null ? "null" : retail, wholesale == null ? "null" : wholesale);
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