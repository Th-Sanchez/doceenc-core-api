package dev.peluci.doceenccoreapi.support;

import dev.peluci.doceenccoreapi.auth.User;
import dev.peluci.doceenccoreapi.auth.UserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TestAuthenticator {

    public static final String REQUIRED_HEADER = "X-Requested-With";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "a-strong-password-123";

    private final MockMvc mockMvc;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public TestAuthenticator(MockMvc mockMvc, UserRepository users, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public Cookie loginAsNewUser() throws Exception {
        users.save(new User(EMAIL, passwordEncoder.encode(PASSWORD)));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .header(REQUIRED_HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
