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
    /** How long the model took to produce each cached reply. */
    private final ConcurrentHashMap<String, Long> computeMillis = new ConcurrentHashMap<>();
    /** Model time the current thread did not spend because replies came from the cache. */
    private final ThreadLocal<Long> reusedMillis = ThreadLocal.withInitial(() -> 0L);

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
        String cached = cache.get(key);
        if (cached != null) {
            reusedMillis.set(reusedMillis.get() + computeMillis.getOrDefault(key, 0L));
            return cached;
        }
        // Not computeIfAbsent: that would hold a map lock for the whole (slow) OpenAI call and
        // block unrelated scans whose keys share the bin. Two identical concurrent misses may both
        // call the API; the first stored response wins so later reads stay consistent.
        long start = System.nanoTime();
        String fresh = apiCall.get();
        if (fresh == null) {
            return null;
        }
        computeMillis.putIfAbsent(key, (System.nanoTime() - start) / 1_000_000);
        String previous = cache.putIfAbsent(key, fresh);
        return previous != null ? previous : fresh;
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
        computeMillis.clear();
    }

    /**
     * Returns the model time the calling thread saved through cache hits since the last call, and
     * resets it. A caller timing a scan adds this to get what the scan costs on its own.
     */
    public long takeReusedMillis() {
        long value = reusedMillis.get();
        reusedMillis.remove();
        return value;
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
