package dev.peluci.doceenccoreapi.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Creates the first user from configuration (INITIAL_USER_EMAIL / INITIAL_USER_PASSWORD) when
 * the users table is empty. There is no sign-up endpoint. Does nothing once any user exists,
 * so it is safe to run on every startup.
 */
@Component
public class InitialUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialUserInitializer.class);

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final InitialUserProperties properties;

    InitialUserInitializer(UserRepository users, PasswordEncoder passwordEncoder,
                           InitialUserProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (users.count() > 0) {
            return;
        }
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            log.warn("No users exist and no initial user is configured " +
                    "(INITIAL_USER_EMAIL / INITIAL_USER_PASSWORD): nobody will be able to log in.");
            return;
        }
        validatePassword(properties.password());
        users.save(new User(properties.email(), passwordEncoder.encode(properties.password())));
        log.info("Initial user crated.");
    }

    private static void validatePassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "INITIAL_USER_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    "INITIAL_USER_PASSWORD must have at most " + MAX_PASSWORD_BYTES + " bytes.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
