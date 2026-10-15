package usv.dizertatie.ai;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory cache for raw AI JSON responses, keyed by SHA-256(outerHTML + modelId + promptVersion).
 *
 * <p>Avoids repeat OpenAI API calls for identical (html, model, promptVersion) triples.
 */
@Component
public class AiResponseCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Returns a cached response if one exists for the given inputs, otherwise invokes
     * {@code apiCall}, stores the result, and returns it.
     *
     * @param html          the rendered outerHTML of the page
     * @param model         the AI model used for analysis
     * @param promptVersion the version string of the system prompt
     * @param apiCall       supplier that performs the actual API call
     * @return the raw JSON response string (from cache or fresh)
     */
    public String getOrCompute(String html, AiModel model, String promptVersion, Supplier<String> apiCall) {
        String key = computeKey(html, model.getModelId(), promptVersion);
        return cache.computeIfAbsent(key, k -> apiCall.get());
    }

    /**
     * Returns the current number of entries in the cache.
     *
     * @return cache size
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
    }

    private String computeKey(String html, String modelId, String promptVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = (html + modelId + promptVersion).getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(input);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java spec; this cannot happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
