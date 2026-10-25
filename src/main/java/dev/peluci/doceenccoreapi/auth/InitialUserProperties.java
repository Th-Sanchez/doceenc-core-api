package dev.peluci.doceenccoreapi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.initial-user")
public record InitialUserProperties(String email, String password) {
}
