package ro.sluta.accessibility.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai")
public record AiAnalysisProperties(
        String baseUrl,
        String apiKey,
        Duration timeout,
        int maxOutputTokens,
        int contextMaxCharacters,
        boolean exploratoryAxeContextEnabled,
        String promptVersion,
        String schemaVersion,
        String contextVersion,
        Map<String, Model> models) {

    public AiAnalysisProperties {
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        maxOutputTokens = maxOutputTokens <= 0 ? 8000 : maxOutputTokens;
        contextMaxCharacters = contextMaxCharacters <= 0 ? 150_000 : contextMaxCharacters;
        models = models == null ? Map.of() : Map.copyOf(models);
    }

    public record Model(
            String providerModel,
            String displayName,
            boolean enabled,
            List<String> modalities,
            List<String> acceptedParameters,
            BigDecimal inputPricePerMillion,
            BigDecimal cachedInputPricePerMillion,
            BigDecimal outputPricePerMillion,
            LocalDate effectiveFrom) {
        public Model {
            modalities = modalities == null ? List.of() : List.copyOf(modalities);
            acceptedParameters = acceptedParameters == null ? List.of() : List.copyOf(acceptedParameters);
        }
    }
}
