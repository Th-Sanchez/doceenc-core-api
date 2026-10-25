package dev.peluci.doceenccoreapi.auth;

import dev.peluci.doceenccoreapi.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
public class InitialUserInitializerIntegrationTest {

    private static final String EMAIL = "Owner@Example.com";
    private static final String PASSWORD = "a-strong-password-123";

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUsers() {
        users.deleteAll();
    }

    @Test
    void createsTheUserWhenNoneExists() throws Exception {
        initializer(EMAIL, PASSWORD).run(null);

        assertThat(users.count()).isEqualTo(1);
        User user = users.findByEmail("owner@example.com").orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void isIdempotent() throws Exception {
        InitialUserInitializer initializer = initializer(EMAIL, PASSWORD);

        initializer.run(null);
        initializer.run(null);

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenNotConfigured() throws Exception {
        initializer("", "").run(null);
        assertThat(users.count()).isZero();
    }

    @Test
    void rejectsAWeakPassword() {
        assertThatThrownBy(() -> initializer(EMAIL, "short").run(null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isZero();
    }

    private InitialUserInitializer initializer(String email, String password) {
        return new InitialUserInitializer(users, passwordEncoder, new InitialUserProperties(email, password));
    }

}
