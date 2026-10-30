package dev.peluci.doceenccoreapi.auth;

import dev.peluci.doceenccoreapi.support.IntegrationTest;
import dev.peluci.doceenccoreapi.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class LoginThrottleIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "a-strong-password-123";
    private static final String WRONG_PASSWORD = "wrong-password-123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setUp() {
        clock.reset();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM login_throttle");
        users.deleteAll();
        users.save(new User(EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        jdbc.update("DELETE FROM login_throttle");
    }

    @Test
    void failuresBeforeTheLimitStillAnswerInvalidCredentials() throws Exception {
        failTimes(EMAIL, 4);

        login(EMAIL, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void afterFiveFailuresTheEmailIsLockedWithRetryAfter() throws Exception {
        failTimes(EMAIL, 5);

        MvcResult result = login(EMAIL, WRONG_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andReturn();

        long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 60L);
    }

    @Test
    void theCorrectPasswordIsRefusedWhileLocked() throws Exception {
        failTimes(EMAIL, 5);

        login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void attemptsRefusedWhileLockedDoNotExtendTheLock() throws Exception {
        failTimes(EMAIL, 5);
        for (int i = 0; i < 10; i++) {
            login(EMAIL, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
        }

        clock.advance(Duration.ofSeconds(61));

        login(EMAIL, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void loginWorksAgainWhenTheLockExpiresAndClearTheCounter() throws Exception {
        failTimes(EMAIL, 5);
        clock.advance(Duration.ofSeconds(61));

        login(EMAIL, PASSWORD).andExpect(status().isNoContent());

        assertThat(throttleRows()).isZero();
    }

    @Test
    void aFailureRightAfterAnExpiredLockLocksAgain() throws Exception {
        failTimes(EMAIL, 5);
        clock.advance(Duration.ofSeconds(61));

        login(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() throws Exception {
        failTimes(EMAIL, 4);
        login(EMAIL, PASSWORD).andExpect(status().isNoContent());

        failTimes(EMAIL, 4);

        login(EMAIL, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void failuresOlderThanTheMemoryWindowsAreForgotten() throws Exception {
        failTimes(EMAIL, 4);
        clock.advance(Duration.ofMinutes(61));

        failTimes(EMAIL, 4);

        login(EMAIL, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void unknownEmailsAreThrottledExactlyLikeKnownOnes() throws Exception {
        failTimes("nobody@example.com", 5);

        login("nobody@example.com", WRONG_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void emailCaseAndSpacesShareTheSameCounter() throws Exception {
        failTimes("USER@Example.com", 3);
        failTimes(" user@example.com ", 2);

        login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void staleRowsArePurged() throws Exception {
        failTimes("old@example.com", 1);
        clock.advance(Duration.ofDays(2));

        failTimes("another@example.com", 1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_throttle WHERE email = 'old@example.com'", Integer.class)).isZero();
    }

    private void failTimes(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                .header("X-Requested-With", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }

    private int throttleRows() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM login_throttle", Integer.class);
        return count == null ? 0 : count;
    }
}
