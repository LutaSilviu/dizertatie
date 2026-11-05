package usv.dizertatie.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@code X-API-Key} header scheme and applies it to every operation, so the
 * "Authorize" button in swagger-ui sends the key. {@code /health} opts out (see HealthController).
 */
@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SCHEME = "ApiKey";

    @Bean
    public OpenAPI accessibilityAnalyzerOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Web Accessibility Analyzer API").version("1.0.0"))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("Value of APP_API_KEY")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
