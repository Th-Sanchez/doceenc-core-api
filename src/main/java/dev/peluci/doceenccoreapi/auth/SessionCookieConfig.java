package dev.peluci.doceenccoreapi.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableConfigurationProperties(SessionCookieProperties.class)
public class SessionCookieConfig {

    @Bean
    CookieSerializer cookieSerializer(SessionCookieProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Strict");
        serializer.setUseSecureCookie(properties.secure());
        return serializer;
    }

}
