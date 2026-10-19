package usv.dizertatie.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import usv.dizertatie.playwright.PageCapture;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
     * Sends the captured page to OpenAI as DOM text plus a full-page PNG screenshot
     * and returns the raw JSON response string.
     * <p>
     * Responses are cached by SHA-256(html + screenshot + modelId + promptVersion), so a
     * page whose DOM is unchanged but whose rendering differs (a CSS-only contrast change,
     * for instance) is analysed afresh rather than served a stale answer.
     *
     * @param capture the rendered HTML and screenshot of the page to analyse
     * @param model   the OpenAI model to use
     * @return the raw JSON string returned by the model (may be from cache)
     * @throws AiAnalysisException if the API call fails
     */
    public String analyze(PageCapture capture, AiModel model) {
        String promptVersion = promptLoader.getVersion();
        String cacheKey = capture.html() + "|" + screenshotDigest(capture.screenshotPng());
        return cache.getOrCompute(cacheKey, model, promptVersion, () -> {
            try {
                String systemPrompt = promptLoader.getPrompt();
                ChatClient chatClient = openAiModelSelector.getClient(model);
                return chatClient
                        .prompt()
                        .system(systemPrompt)
                        .user(userSpec -> userSpec
                                .text(capture.html())
                                .media(screenshotMedia(capture.screenshotPng())))
                        .call()
                        .content();
            } catch (Exception e) {
                throw new AiAnalysisException("AI analysis failed for model " + model.getModelId(), e);
            }
        });
    }

    private static Media screenshotMedia(byte[] screenshotPng) {
        return new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(screenshotPng));
    }

    private static String screenshotDigest(byte[] screenshotPng) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(screenshotPng));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
