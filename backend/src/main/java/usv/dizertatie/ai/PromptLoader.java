package usv.dizertatie.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptLoader {

    private static final String PROMPT_PATH = "prompts/accessibility-analysis.txt";

    /**
     * Loads and returns the system prompt for accessibility analysis.
     */
    public String getPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load AI prompt from " + PROMPT_PATH, e);
        }
    }
}
