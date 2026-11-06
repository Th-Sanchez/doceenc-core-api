package dev.peluci.doceenccoreapi.shared.config;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ProblemSchemaConfig {

    @Bean
    OpenApiCustomizer problemDetailSchema() {
        return openApi -> {
            Schema<?> fieldProblem = new ObjectSchema()
                    .addProperty("field", new StringSchema().description("Field path, e.g. \"price.packagePrice\"."))
                    .addProperty("code", new StringSchema().description("Bean validation constraint, e.g.\"NotBlank\"."));
            fieldProblem.setRequired(List.of("field", "code"));

            Schema<?> problem = new ObjectSchema()
                    .addProperty("type", new StringSchema())
                    .addProperty("title", new StringSchema())
                    .addProperty("status", new StringSchema())
                    .addProperty("detail", new StringSchema())
                    .addProperty("instance", new StringSchema())
                    .addProperty("code", new StringSchema()
                            .description("Stable error code; the UI text is chosen from it."))
                    .addProperty("retryAfterSeconds", new IntegerSchema().format("int64"))
                    .addProperty("errors", new ArraySchema()
                            .items(new Schema<>().$ref("#/components/schemas/FieldProblem"))
                            .description("Only for VALIDATION_FAILED."));
            problem.setRequired(List.of("status", "code"));

            openApi.getComponents()
                    .addSchemas("FieldProblem", fieldProblem)
                    .addSchemas("ProblemDetail", problem);
        };
    }

}
