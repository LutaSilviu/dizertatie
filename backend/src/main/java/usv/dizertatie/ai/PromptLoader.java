package usv.dizertatie.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the versioned accessibility analysis system prompt from the classpath.
 *
 * <p>The prompt is stored at {@code prompts/accessibility-v1.txt}. The version
 * string is derived from the filename convention {@code accessibility-<version>.txt}.
 */
@Component
public class PromptLoader {

    private static final String PROMPT_RESOURCE = "prompts/accessibility-v1.txt";
    private static final String VERSION = "v1";

    private String promptText;

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(PROMPT_RESOURCE);
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
     * @return {@code "v1"}
     */
    public String getVersion() {
        return VERSION;
    }
}
