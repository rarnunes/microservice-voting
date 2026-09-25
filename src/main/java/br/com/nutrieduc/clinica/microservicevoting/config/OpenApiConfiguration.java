package br.com.nutrieduc.clinica.microservicevoting.config;

import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI votingOpenApi() {
        Schema<?> error = new Schema<>().type("object")
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty("status", new IntegerSchema())
                .addProperty("error", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("path", new StringSchema());
        Components components = new Components().addSchemas("ApiErrorResponse", error);
        Map.of("400", "Request inválido", "404", "Pauta ou sessão não encontrada", "409", "Conflito de regra de negócio")
                .forEach((status, description) -> components.addResponses(status, new ApiResponse()
                        .description(description).content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))))));
        return new OpenAPI().info(new Info().title("Voting API").version("v1")
                .description("API de pautas e votação. Datas em UTC; escolhas YES e NO."))
                .components(components);
    }

    @Bean
    public OpenApiCustomizer votingErrorResponses() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            operation.getResponses().addApiResponse("400", new ApiResponse().$ref("#/components/responses/400"));
            if (path.contains("{agendaId}")) {
                operation.getResponses().addApiResponse("404", new ApiResponse().$ref("#/components/responses/404"));
            }
            if (path.endsWith("/votes") || path.endsWith("/sessions")) {
                operation.getResponses().addApiResponse("409", new ApiResponse().$ref("#/components/responses/409"));
            }
        }));
    }
}
