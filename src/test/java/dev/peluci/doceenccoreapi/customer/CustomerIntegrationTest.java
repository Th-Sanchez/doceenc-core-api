package dev.peluci.doceenccoreapi.customer;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.auth.UserRepository;
import dev.peluci.doceenccoreapi.support.DatabaseCleaner;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.TestAuthenticator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class CustomerIntegrationTest {

    private static final String BASE = "/api/v1/customers";
    private static final String HEADER = TestAuthenticator.REQUIRED_HEADER;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    DatabaseCleaner cleaner;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        cleaner.clean();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();
    }

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        UUID id = UUID.randomUUID();
        String body = customerJson("Maria", "RETAIL", null, null, null, null);

        List<RequestBuilder> requests = List.of(
                get(BASE),
                get(BASE + "/" + id),
                post(BASE).header(HEADER, "test").contentType(MediaType.APPLICATION_JSON).content(body),
                put(BASE + "/" + id).header(HEADER, "test").contentType(MediaType.APPLICATION_JSON).content(body),
                post(BASE + "/" + id + "/archive").header(HEADER, "test"),
                post(BASE + "/" + id + "/restore").header(HEADER, "test"));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void createsRetailAndWholesaleCustomers() throws Exception {
        doPost(BASE, customerJson("Maria Silva", "RETAIL", "11988887777", "Rua X, 123", null, "Sem glúten"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.type").value("RETAIL"))
                .andExpect(jsonPath("$.phone").value("11988887777"))
                .andExpect(jsonPath("$.address").value("Rua X, 123"))
                .andExpect(jsonPath("$.storeName").doesNotExist())
                .andExpect(jsonPath("$.notes").value("Sem glúten"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        doPost(BASE, customerJson("João Souza", "WHOLESALE", "1133334444", null, "Doces do João", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WHOLESALE"))
                .andExpect(jsonPath("$.storeName").value("Doces do João"))
                .andExpect(jsonPath("$.address").doesNotExist());
    }

    @Test
    void createNormalizesInputBlankBecomesNullAndPhoneKeepsOnlyDigits() throws Exception {
        doPost(BASE, customerJson("  Maria  ", "WHOLESALE", "(11) 99999-9999", "   ", "  Doces da Maria ", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Maria"))
                .andExpect(jsonPath("$.phone").value("11999999999"))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.storeName").value("Doces da Maria"))
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void twoCustomersMayShareTheSameName() throws Exception {
        createCustomer("João", "RETAIL", null, null, null, null);
        createCustomer("João", "RETAIL", null, null, null, null);

        doGet(BASE).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void storeNameIsRequiredForWholesaleAndForbiddenForRetail() throws Exception {
        assertRejectedField(customerJson("Maria", "RETAIL", null, null, "Loja", null), "storeName");
        assertRejectedField(customerJson("Maria", "WHOLESALE", null, null, null, null), "storeName");
        assertRejectedField(customerJson("Maria", "WHOLESALE", null, null, "   ", null), "storeName");

        doGet(BASE).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidFieldsAreReportedByName() throws Exception {
        assertRejectedField(customerJson("   ", "RETAIL", null, null, null, null), "name");
        assertRejectedField(customerJson("a".repeat(121), "RETAIL", null, null, null, null), "name");
        assertRejectedField(customerJson("Maria", null, null, null, null, null), "type");
        assertRejectedField(customerJson("Maria", "RETAIL", "123", null, null, null), "phone");
        assertRejectedField(customerJson("Maria", "RETAIL", "abc", null, null, null), "phone");
        assertRejectedField(customerJson("Maria", "RETAIL", null, "a".repeat(256), null, null), "address");
        assertRejectedField(customerJson("Maria", "RETAIL", null, null, null, "a".repeat(1001)), "notes");

        doPost(BASE, customerJson("Maria", "OTHER", null, null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getReturnsTheCustomerAndAnUnknownIdIsNotFound() throws Exception {
        UUID id = createCustomer("Maria Silva", "WHOLESALE", "11999999999", "Rua X, 123", "Doces da Maria", "obs");

        doGet(BASE + "/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.storeName").value("Doces da Maria"))
                .andExpect(jsonPath("$.notes").value("obs"));

        doGet(BASE + "/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void updateReplacesAllEditableFieldsIncludingTheTypeBothWays() throws Exception {
        UUID id = createCustomer("Maria Silva", "WHOLESALE", "11999999999", "Rua X, 123", "Doces da Maria", "obs");
        String createdAt = JsonPath.read(doGet(BASE + "/" + id).andReturn().getResponse().getContentAsString(),
                "$.createdAt");

        String body = doPut(BASE + "/" + id, customerJson("Maria S.", "RETAIL", null, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Maria S."))
                .andExpect(jsonPath("$.type").value("RETAIL"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.storeName").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andReturn().getResponse().getContentAsString();

        // the audit column in the PUT response must match what was persisted (guards the flush in the service)
        String updatedAt = JsonPath.read(body, "$.updatedAt");
        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.updatedAt").value(updatedAt))
                .andExpect(jsonPath("$.type").value("RETAIL"));

        doPut(BASE + "/" + id, customerJson("Maria S.", "WHOLESALE", null, null, "Doces da Maria", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WHOLESALE"))
                .andExpect(jsonPath("$.storeName").value("Doces da Maria"));
    }

    @Test
    void updateWithAnInconsistentStoreNameIsRejectedAndKeepsThePreviousState() throws Exception {
        UUID id = createCustomer("Maria Silva", "WHOLESALE", null, null, "Doces da Maria", null);

        doPut(BASE + "/" + id, customerJson("Outro nome", "RETAIL", null, null, "Doces da Maria", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'storeName')]").exists());

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.type").value("WHOLESALE"))
                .andExpect(jsonPath("$.storeName").value("Doces da Maria"));
    }

    @Test
    void archiveAndRestoreAreIdempotentAndKeepTheData() throws Exception {
        UUID id = createCustomer("Maria Silva", "WHOLESALE", "11999999999", null, "Doces da Maria", null);

        String archived = doPost(BASE + "/" + id + "/archive", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn().getResponse().getContentAsString();

        // archiving again is a no-op: same state, no spurious modification
        doPost(BASE + "/" + id + "/archive", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.updatedAt").value((String) JsonPath.read(archived, "$.updatedAt")));

        doGet(BASE + "/" + id)
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.storeName").value("Doces da Maria"));

        doPost(BASE + "/" + id + "/restore", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        doPost(BASE + "/" + id + "/restore", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void anArchivedCustomerIsReadOnlyUntilRestored() throws Exception {
        UUID id = createCustomer("Maria Silva", "RETAIL", null, null, null, null);
        doPost(BASE + "/" + id + "/archive", "").andExpect(status().isOk());

        doPut(BASE + "/" + id, customerJson("Novo nome", "RETAIL", null, null, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ARCHIVED"));

        doGet(BASE + "/" + id).andExpect(jsonPath("$.name").value("Maria Silva"));

        doPost(BASE + "/" + id + "/restore", "").andExpect(status().isOk());
        doPut(BASE + "/" + id, customerJson("Novo nome", "RETAIL", null, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo nome"));
    }

    @Test
    void unknownIdIsNotFoundForUpdateArchiveAndRestore() throws Exception {
        UUID unknown = UUID.randomUUID();

        doPut(BASE + "/" + unknown, customerJson("Maria", "RETAIL", null, null, null, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        doPost(BASE + "/" + unknown + "/archive", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        doPost(BASE + "/" + unknown + "/restore", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void listReturnsActiveAndArchivedSortedByNameIgnoringCase() throws Exception {
        createCustomer("joão", "RETAIL", null, null, null, null);
        createCustomer("Ana", "WHOLESALE", null, null, "Doces da Ana", null);
        UUID bia = createCustomer("Bia", "RETAIL", null, null, null, null);
        doPost(BASE + "/" + bia + "/archive", "").andExpect(status().isOk());

        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].name").value("Bia"))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[2].name").value("joão"))
                .andExpect(jsonPath("$[2].active").value(true));
    }

    @Test
    void databaseRejectsInconsistentRowsEvenWithoutTheApi() {
        String insert = """
                INSERT INTO customer (id, name, type, store_name, active, created_at, updated_at)
                VALUES (gen_random_uuid(), 'X', ?, ?, true, now(), now())""";

        assertThatThrownBy(() -> jdbc.update(insert, "RETAIL", "Loja"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "WHOLESALE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "WHOLESALE", "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "OTHER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertRejectedField(String body, String field) throws Exception {
        doPost(BASE, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());
    }

    private UUID createCustomer(String name, String type, String phone, String address,
                                String storeName, String notes) throws Exception {
        String body = doPost(BASE, customerJson(name, type, phone, address, storeName, notes))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String customerJson(String name, String type, String phone, String address,
                                       String storeName, String notes) {
        return """
                {"name":%s,"type":%s,"phone":%s,"address":%s,"storeName":%s,"notes":%s}"""
                .formatted(quoted(name), quoted(type), quoted(phone), quoted(address),
                        quoted(storeName), quoted(notes));
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
