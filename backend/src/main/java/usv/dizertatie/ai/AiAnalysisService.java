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
                                .text(htmlForPrompt(capture.html()))
                                .media(screenshotMedia(capture.screenshotPng())))
                        .call()
                        .content();
            } catch (Exception e) {
                throw new AiAnalysisException("AI analysis failed for model " + model.getModelId(), e);
            }
        });
    }

    /**
     * Upper bound on the HTML sent to the model (~50k tokens). Real-world pages often ship
     * megabytes of inline scripts and styles, which would exceed the model's context window
     * and fail the whole scan.
     */
    static final int MAX_PROMPT_HTML_CHARS = 200_000;

    private static final java.util.regex.Pattern NOISE = java.util.regex.Pattern.compile(
            "(?is)<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>|<!--.*?-->");

    /** Keeps the {@code <svg>} element (and its title/aria attributes) but drops its drawing data. */
    private static final java.util.regex.Pattern SVG_BODY = java.util.regex.Pattern.compile(
            "(?is)(<svg\\b[^>]*>).*?(</svg>)");

    /**
     * Returns the HTML unchanged when it fits; otherwise drops script and style bodies,
     * comments and inline SVG drawing data (none of which the audit needs) and, if still too
     * long, truncates with a marker so the model knows the document was cut.
     */
    static String htmlForPrompt(String html) {
        if (html == null || html.length() <= MAX_PROMPT_HTML_CHARS) {
            return html;
        }
        String reduced = NOISE.matcher(html).replaceAll("");
        reduced = SVG_BODY.matcher(reduced).replaceAll("$1$2");
        if (reduced.length() <= MAX_PROMPT_HTML_CHARS) {
            return reduced;
        }
        return reduced.substring(0, MAX_PROMPT_HTML_CHARS)
                + "\n<!-- [document truncated: " + (reduced.length() - MAX_PROMPT_HTML_CHARS)
                + " more characters not shown] -->";
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
