package usv.dizertatie.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class OpenAiModelSelector {

    private final ChatClient.Builder chatClientBuilder;

    public OpenAiModelSelector(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * Returns a {@link ChatClient} configured with the given model at temperature 0.
     */
    public ChatClient getClient(AiModel model) {
        return chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model.getModelId())
                        .temperature(0.0))
                .build();
    }

    /**
     * Returns the OpenAI model ID string for the given {@link AiModel}.
     */
    public String getModelId(AiModel model) {
        return model.getModelId();
    }
}
