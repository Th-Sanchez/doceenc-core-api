package dev.peluci.doceenccoreapi;

import com.jayway.jsonpath.JsonPath;
import dev.peluci.doceenccoreapi.auth.UserRepository;
import dev.peluci.doceenccoreapi.support.DatabaseCleaner;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.TestAuthenticator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class OpenApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    DatabaseCleaner cleaner;

    @Test
    void everyPropertyOfAResponseSchemaIsRequired() throws Exception {
        cleaner.clean();
        Cookie session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();

        String json = mockMvc.perform(get("/v3/api-docs").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Map<String, Object>> schemas = JsonPath.read(json, "$.components.schemas");
        List<String> violations = new ArrayList<>();
        schemas.forEach((name, schema) -> {
            boolean isResponse = name.endsWith("Response") || name.equals("CategorySummary");
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (!isResponse || properties == null) {
                return;
            }
            Collection<?> required = (Collection<?>) schema.getOrDefault("required", List.of());
            properties.keySet().stream()
                    .filter(property -> !required.contains(property))
                    .forEach(property -> violations.add(name + "." + property));
        });

        assertThat(violations)
                .as("response properties missing @Schema(requiredMode = REQUIRED)")
                .isEmpty();

    }

    @Test
    void problemDetailDocumentsTheFieldsTheApiReallySends() throws Exception {
        cleaner.clean();
        Cookie session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();

        String json = mockMvc.perform(get("/v3/api-docs").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> properties = JsonPath.read(json, "$.components.schemas.ProblemDetail.properties");
        List<String> required = JsonPath.read(json, "$.components.schemas.ProblemDetail.required");

        assertThat(properties).containsKeys("code", "errors", "retryAfterSeconds");
        assertThat(required).contains("code");
        assertThat(json).contains("\"FieldProblem\"");
    }

    @Test
    void errorsRaisedBySpringMvcAlsoCarryACode() throws Exception {
        cleaner.clean();
        Cookie session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();

        mockMvc.perform(get("/api/v1/does-not-exist").cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_ERROR"));
    }

}
