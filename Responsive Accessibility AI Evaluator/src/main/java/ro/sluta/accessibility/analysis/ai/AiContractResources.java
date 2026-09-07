package ro.sluta.accessibility.analysis.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.config.AiAnalysisProperties;

@Component
public class AiContractResources {
    private final AiAnalysisProperties properties;

    public AiContractResources(AiAnalysisProperties properties) { this.properties = properties; }

    public String prompt() { return read("ai/" + properties.promptVersion() + ".txt"); }
    public String schema() { return read("ai/" + properties.schemaVersion() + ".json"); }

    private String read(String path) {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Resursa AI versionată lipsește: " + path, exception);
        }
    }
}
