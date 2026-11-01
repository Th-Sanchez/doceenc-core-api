package dev.peluci.doceenccoreapi.category;

import dev.peluci.doceenccoreapi.auth.UserRepository;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.TestAuthenticator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class CategoryIntegrationTest {

    private static final String BASE = "/api/v1/categories";
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
    JdbcTemplate jdbc;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM ingredient_price_history");
        jdbc.update("DELETE FROM ingredient");
        jdbc.update("DELETE FROM category");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM login_throttle");
        users.deleteAll();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();
    }

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void createReturnsTheCategoryWithATrimmedName() throws Exception {
        mockMvc.perform(post(BASE).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"  Farinhas  ","type":"INGREDIENT"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Farinhas"))
                .andExpect(jsonPath("$.type").value("INGREDIENT"));
    }

    @Test
    void duplicateNameInTheSameTypeIsRejectedIgnoringCase() throws Exception {
        categories.save(new Category("Farinhas", CategoryType.INGREDIENT));

        mockMvc.perform(post(BASE).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"FARINHAS","type":"INGREDIENT"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
    }

    @Test
    void theSameNameIsAllowedInADifferentType() throws Exception {
        categories.save(new Category("Bolos", CategoryType.INGREDIENT));

        mockMvc.perform(post(BASE).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Bolos","type":"RECIPE"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidPayloadReturnsValidationFailedWithTheOffendingFields() throws Exception {
        mockMvc.perform(post(BASE).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"   ","type":"INGREDIENT"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
    }

    @Test
    void unknownTypeReturnsMalformedRequest() throws Exception {
        mockMvc.perform(post(BASE).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Farinhas","type":"BANANA"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void listFiltersByTypeAndOrdersByName() throws Exception {
        categories.save(new Category("Recheios", CategoryType.INGREDIENT));
        categories.save(new Category("Farinhas", CategoryType.INGREDIENT));
        categories.save(new Category("Bolos", CategoryType.RECIPE));

        mockMvc.perform(get(BASE).param("type", "INGREDIENT").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Farinhas"))
                .andExpect(jsonPath("$[1].name").value("Recheios"));
    }

    @Test
    void updateRenamesAndAllowsChangingOnlyTheCaseOfItsOwnName() throws Exception {
        Category category = categories.save(new Category("Farinhas", CategoryType.INGREDIENT));

        mockMvc.perform(put(BASE + "/{id}", category.getId()).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"farinhas"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("farinhas"));
    }

    @Test
    void updateToTheNameOfAnotherCategoryIsRejected() throws Exception {
        categories.save(new Category("Farinhas", CategoryType.INGREDIENT));
        Category other = categories.save(new Category("Recheios", CategoryType.INGREDIENT));

        mockMvc.perform(put(BASE + "/{id}", other.getId()).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Farinhas"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
    }

    @Test
    void updateOfAnUnknownCategoryIsNotFound() throws Exception {
        mockMvc.perform(put(BASE + "/{id}", UUID.randomUUID()).cookie(session).header(HEADER, "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"name":"Farinhas"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void deleteRemovesAnUnusedCategory() throws Exception {
        Category category = categories.save(new Category("Farinhas", CategoryType.INGREDIENT));

        mockMvc.perform(delete(BASE + "/{id}", category.getId()).cookie(session).header(HEADER, "test"))
                .andExpect(status().isNoContent());

        assertThat(categories.existsById(category.getId())).isFalse();
    }

    @Test
    void deleteOfACategoryInUseIsAConflictAndKeepsTheCategory() throws Exception {
        Category category = categories.save(new Category("Farinhas", CategoryType.INGREDIENT));
        jdbc.update("""
                INSERT INTO ingredient (id, name, category_id, base_unit, package_price,
                                        package_quantity, unit_price, active, created_at, updated_at)
                VALUES (?, 'Farinha de trigo', ?, 'GRAM', 8.90, 1000, 0.0089, TRUE, NOW(), NOW())""",
                UUID.randomUUID(), category.getId());

        mockMvc.perform(delete(BASE + "/{id}", category.getId()).cookie(session).header(HEADER, "test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        assertThat(categories.existsById(category.getId())).isTrue();
    }

}
