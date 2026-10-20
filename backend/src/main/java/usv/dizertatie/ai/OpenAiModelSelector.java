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

    /** Returns a ChatClient configured for the selected OpenAI model. */
    public ChatClient getClient(AiModel model) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(model.getModelId());
        if (model == AiModel.GPT6_LUNA) {
            options.reasoningEffort("none");
        }
        options.temperature(0.0);

        return chatClientBuilder
                .defaultOptions(options)
                .build();
    }

    /**
     * Returns the OpenAI model ID string for the given {@link AiModel}.
     */
    public String getModelId(AiModel model) {
        return model.getModelId();
    }
}
