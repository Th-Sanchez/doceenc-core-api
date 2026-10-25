package dev.peluci.doceenccoreapi.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SESSION_SCHEME = "sessionCookie";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Doce Encanto Core API")
                        .version("v1")
                        .description("Session-base API. Authenticate with POST /api/v1/auth/login. " +
                                "Every request that changes state must send the X-Requested-With header. " +
                                "Errors are RFC 9457 problem details with a stable \"code\" property,"))
                .components(new Components().addSecuritySchemes(SESSION_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }

}
