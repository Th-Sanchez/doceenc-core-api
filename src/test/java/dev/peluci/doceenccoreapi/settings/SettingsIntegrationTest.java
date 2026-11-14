package dev.peluci.doceenccoreapi.settings;

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
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class SettingsIntegrationTest {

    private static final String BASE = "/api/v1/settings";
    private static final String HEADER = TestAuthenticator.REQUIRED_HEADER;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    DatabaseCleaner cleaner;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        cleaner.clean();
        session = new TestAuthenticator(mockMvc, users, passwordEncoder).loginAsNewUser();
    }

    @Test
    void requestsWithoutASessionAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void theSettingsRowExistsFromTheStartWithTheBaselineTargets() throws Exception {
        doGet(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetMarginRetail").value(50.0))
                .andExpect(jsonPath("$.targetMarginWholesale").value(35.0))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void updateChangesBothTargetsAndTheyPersist() throws Exception {
        doPut(BASE, targets("42.50", "30.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetMarginRetail").value(42.5))
                .andExpect(jsonPath("$.targetMarginWholesale").value(30.0));

        doGet(BASE)
                .andExpect(jsonPath("$.targetMarginRetail").value(42.5))
                .andExpect(jsonPath("$.targetMarginWholesale").value(30.0));
    }

    @Test
    void theBoundariesZeroAndNinetyNineNinetyNineAreAccepted() throws Exception {
        doPut(BASE, targets("0.00", "99.99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetMarginRetail").value(0.0))
                .andExpect(jsonPath("$.targetMarginWholesale").value(99.99));
    }

    @Test
    void outOfRangeValuesExtraDecimalsAndMissingFieldsAreRejectedAndChangeNothing() throws Exception {
        doPut(BASE, targets("100", "35.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'targetMarginRetail')]").exists());

        doPut(BASE, targets("50.00", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'targetMarginWholesale')]").exists());

        doPut(BASE, targets("50.123", "35.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        doPut(BASE, """
                {"targetMarginRetail":50.00}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'targetMarginWholesale')]").exists());

        doGet(BASE)
                .andExpect(jsonPath("$.targetMarginRetail").value(50.0))
                .andExpect(jsonPath("$.targetMarginWholesale").value(35.0));
    }

    private static String targets(String retail, String wholesale) {
        return """
                {"targetMarginRetail":%s,"targetMarginWholesale":%s}""".formatted(retail, wholesale);
    }

    private ResultActions doGet(String url) throws Exception {
        return mockMvc.perform(get(url).cookie(session));
    }

    private ResultActions doPut(String url, String body) throws Exception {
        return mockMvc.perform(put(url).cookie(session).header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

}