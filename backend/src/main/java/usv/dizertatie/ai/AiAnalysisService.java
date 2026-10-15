package usv.dizertatie.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiAnalysisService {

    private final OpenAiModelSelector openAiModelSelector;
    private final PromptLoader promptLoader;
    private final AiResponseCache cache;

    public AiAnalysisService(OpenAiModelSelector openAiModelSelector,
                             PromptLoader promptLoader,
                             AiResponseCache cache) {
        this.openAiModelSelector = openAiModelSelector;
        this.promptLoader = promptLoader;
        this.cache = cache;
    }

    /**
     * Sends the given HTML to OpenAI and returns the raw JSON response string.
     * Responses are cached by SHA-256(html + modelId + promptVersion) to avoid
     * repeat API calls for identical inputs.
     *
     * @param html  the rendered HTML of the page to analyse
     * @param model the OpenAI model to use
     * @return the raw JSON string returned by the model (may be from cache)
     * @throws AiAnalysisException if the API call fails
     */
    public String analyze(String html, AiModel model) {
        String promptVersion = promptLoader.getVersion();
        return cache.getOrCompute(html, model, promptVersion, () -> {
            try {
                String systemPrompt = promptLoader.getPrompt();
                ChatClient chatClient = openAiModelSelector.getClient(model);
                return chatClient
                        .prompt()
                        .system(systemPrompt)
                        .user(html)
                        .call()
                        .content();
            } catch (Exception e) {
                throw new AiAnalysisException("AI analysis failed for model " + model.getModelId(), e);
            }
        });
    }
}
