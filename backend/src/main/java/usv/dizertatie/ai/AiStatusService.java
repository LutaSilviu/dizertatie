package usv.dizertatie.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import usv.dizertatie.error.FailureMessages;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Reports whether AI scans can work right now, so the UI can explain (or disable) AI options
 * instead of letting users wait for a scan that will fail.
 *
 * <p>A key that is blank or an obvious placeholder is reported without any network call. Otherwise
 * the key is checked with {@code GET /v1/models}, which is free. Results are cached for
 * {@link #CACHE_TTL}. Messages never contain the key or any fragment of it.
 */
@Service
public class AiStatusService {

    private static final Logger log = LoggerFactory.getLogger(AiStatusService.class);

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    public enum Status { OK, MISSING_KEY, INVALID_KEY, UNREACHABLE }

    public record AiStatus(Status status, String message) {}

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Clock clock;

    private AiStatus cached;
    private Instant cachedAt;

    @Autowired
    public AiStatusService(@Value("${spring.ai.openai.api-key:}") String apiKey,
                           @Value("${spring.ai.openai.base-url:}") String baseUrl) {
        this(apiKey, baseUrl, HttpClient.newBuilder().connectTimeout(CHECK_TIMEOUT).build(), Clock.systemUTC());
    }

    AiStatusService(String apiKey, String baseUrl, HttpClient httpClient, Clock clock) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.strip();
        this.httpClient = httpClient;
        this.clock = clock;
    }

    /** {@code true} when no usable key is configured; needs no network call. */
    public boolean isKeyMissing() {
        return isPlaceholder(apiKey);
    }

    static boolean isPlaceholder(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String k = key.strip().toLowerCase(Locale.ROOT);
        return k.equals("test-key") || k.equals("sk-...") || k.contains("placeholder")
                || k.contains("your-api-key") || k.contains("your_api_key") || k.equals("changeme");
    }

    public synchronized AiStatus status() {
        Instant now = clock.instant();
        if (cached != null && cachedAt != null && now.isBefore(cachedAt.plus(CACHE_TTL))) {
            return cached;
        }
        cached = check();
        cachedAt = now;
        return cached;
    }

    private AiStatus check() {
        if (isKeyMissing()) {
            return new AiStatus(Status.MISSING_KEY, FailureMessages.OPENAI_KEY_MISSING);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(modelsUri())
                    .timeout(CHECK_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            int code = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code == 401 || code == 403) {
                return new AiStatus(Status.INVALID_KEY, FailureMessages.OPENAI_KEY_REJECTED);
            }
            if (code == 429) {
                return new AiStatus(Status.OK, "OpenAI accepted the API key but is currently rate limiting requests.");
            }
            if (code >= 200 && code < 300) {
                return new AiStatus(Status.OK, "OpenAI is reachable and the API key is valid.");
            }
            return new AiStatus(Status.UNREACHABLE, "OpenAI answered with HTTP " + code + "; AI scans may fail.");
        } catch (IOException e) {
            log.warn("OpenAI status check failed: {}", e.toString());
            return new AiStatus(Status.UNREACHABLE,
                    "OpenAI could not be reached from the backend (network error or timeout).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AiStatus(Status.UNREACHABLE, "OpenAI status check was interrupted.");
        } catch (IllegalArgumentException e) {
            return new AiStatus(Status.UNREACHABLE, "The configured OpenAI base URL is not valid.");
        }
    }

    private URI modelsUri() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!base.endsWith("/v1")) {
            base = base + "/v1";
        }
        return URI.create(base + "/models");
    }
}
