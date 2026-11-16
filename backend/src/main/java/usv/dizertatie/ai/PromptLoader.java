package usv.dizertatie.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the versioned accessibility analysis system prompt from the classpath.
 *
 * <p>The prompt is stored at {@code prompts/accessibility-<version>.txt}; the version is chosen with
 * {@code app.ai.prompt-version} (environment variable {@code AI_PROMPT_VERSION}), so results of
 * different prompt versions can be compared. v2 adds the selector and evidence rules. The version
 * string is derived from the filename convention {@code accessibility-<version>.txt}.
 */
@Component
public class PromptLoader {

    private final String version;

    public PromptLoader(@org.springframework.beans.factory.annotation.Value("${app.ai.prompt-version:v2}") String version) {
        if (version == null || !version.matches("v\\d+")) {
            throw new IllegalArgumentException("app.ai.prompt-version must look like v1, v2, ...: " + version);
        }
        this.version = version;
    }

    private String promptText;

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/accessibility-" + version + ".txt");
        promptText = resource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Returns the accessibility analysis system prompt text.
     *
     * @return the full prompt string, never {@code null}
     */
    public String getPrompt() {
        return promptText;
    }

    /**
     * Returns the version identifier of the currently loaded prompt.
     *
     * @return the configured version, e.g. {@code "v2"}
     */
    public String getVersion() {
        return version;
    }
}
