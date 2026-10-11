package usv.dizertatie.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiAnalysisService {

    private final OpenAiModelSelector openAiModelSelector;
    private final PromptLoader promptLoader;

    public AiAnalysisService(OpenAiModelSelector openAiModelSelector, PromptLoader promptLoader) {
        this.openAiModelSelector = openAiModelSelector;
        this.promptLoader = promptLoader;
    }

    /**
     * Sends the given HTML to OpenAI and returns the raw JSON response string.
     *
     * @param html  the rendered HTML of the page to analyse
     * @param model the OpenAI model to use
     * @return the raw JSON string returned by the model
     * @throws AiAnalysisException if the API call fails
     */
    public String analyze(String html, AiModel model) {
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
    }
}
