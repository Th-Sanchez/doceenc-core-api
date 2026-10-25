package dev.peluci.doceenccoreapi.auth;

import dev.peluci.doceenccoreapi.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestPropertySource(properties = "logging.level.org.springframework.security=DEBUG")
public class AuthIntegrationTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "a-strong-password-123";
    private static final String REQUIRED_HEADER = "X-Requested-With";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from spring_session");
        users.deleteAll();
        users.save(new User(EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @Test
    void loginWithValidCredentialsCreatesAHardenedSessionCookie() throws Exception {
        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isNoContent()).andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("SESSION=").contains("HttpOnly").contains("SameSite=Strict");
        assertThat(sessionCount()).isEqualTo(1);
    }

    @Test
    void loginIgnoresEmailCase() throws Exception {
        login("USER@Example.com", PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void meReturnsTheCurrentUserWhenTheSessionCookieIsSent() throws Exception {
        Cookie session = loginAndGetSessionCookie();

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameResponse() throws Exception {
        String wrongPassword = login(EMAIL, "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void invalidLoginPayloadIsABadRequest() throws Exception {
        login("", "").andExpect(status().isBadRequest());
    }

    @Test
    void requestThatChangeStateWithoutTheRequiredHeaderAreForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_CSRF_HEADER"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        Cookie session = loginAndGetSessionCookie();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session).header(REQUIRED_HEADER, "test"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
        assertThat(sessionCount()).isZero();
    }

    @Test
    void unauthenticatedRequestsGetAProblemResponseAndCreateNoSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(sessionCount()).isZero();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header(REQUIRED_HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    private Cookie loginAndGetSessionCookie() throws Exception {
        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isNoContent()).andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    private int sessionCount() {
        Integer count = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
        return count == null ? 0 : count;
    }

}
