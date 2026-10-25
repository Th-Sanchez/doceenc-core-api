package dev.peluci.doceenccoreapi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.session.cookie")
public record SessionCookieProperties(boolean secure) {
}
