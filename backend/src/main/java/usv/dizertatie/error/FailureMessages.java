package usv.dizertatie.error;

import com.microsoft.playwright.PlaywrightException;
import usv.dizertatie.ai.AiAnalysisException;
import usv.dizertatie.ai.AiKeyMissingException;
import usv.dizertatie.playwright.PageBlockedException;
import usv.dizertatie.playwright.PageCaptureException;
import usv.dizertatie.playwright.PageNavigator;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns scan failures into one-line messages a user can act on. Shared by the REST error handler
 * and the benchmark runner, so a failure reads the same in a scan response and a run summary.
 */
public final class FailureMessages {

    public static final String OPENAI_KEY_REJECTED =
            "OpenAI rejected the API key. Set a valid OPENAI_API_KEY in .env and restart the backend.";

    /** Also the {@code MISSING_KEY} message of {@code GET /ai/status}, so the UI never shows two versions. */
    public static final String OPENAI_KEY_MISSING =
            "No OpenAI API key is configured. Set OPENAI_API_KEY in .env and restart the backend; "
                    + "AXE scans work without it.";

    /** OpenAI echoes a masked key ("sk-proj-abc***xyz"); no fragment of it may reach a client. */
    private static final Pattern KEY_FRAGMENT = Pattern.compile("sk-[A-Za-z0-9*_\\-.]{2,}");

    private FailureMessages() {
    }

    /** {@code true} when OpenAI refused the credentials (HTTP 401/403, invalid key). */
    public static boolean isAiAuthFailure(Throwable e) {
        if (!(e instanceof AiAnalysisException) || e instanceof AiKeyMissingException) {
            return false;
        }
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getSimpleName();
            if (name.contains("Unauthorized") || name.contains("PermissionDenied")) {
                return true;
            }
            String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            if (msg.contains("401") || msg.contains("invalid_api_key") || msg.contains("incorrect api key")
                    || msg.contains("api key") || msg.contains("unauthorized")) {
                return true;
            }
        }
        return false;
    }

    /** A human-readable, key-free, single-line description of why a scan failed. */
    public static String describe(Throwable e) {
        if (e == null) {
            return "Unknown error";
        }
        if (e instanceof AiKeyMissingException) {
            return OPENAI_KEY_MISSING;
        }
        if (e instanceof AiAnalysisException && isAiAuthFailure(e)) {
            return OPENAI_KEY_REJECTED;
        }
        if (e instanceof AiAnalysisException) {
            return redact(e.getMessage() + ": " + firstLine(rootMessage(e)));
        }
        if (e instanceof PageCaptureException || e instanceof PageBlockedException) {
            return redact(firstLine(e.getMessage()));
        }
        if (e instanceof PlaywrightException) {
            return "Browser could not load or audit the page: " + PageNavigator.describeBrowserError(e.getMessage());
        }
        return redact(firstLine(rootMessage(e)));
    }

    public static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }

    public static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.strip();
        int nl = trimmed.indexOf('\n');
        return nl < 0 ? trimmed : trimmed.substring(0, nl).strip();
    }

    public static String redact(String message) {
        return message == null ? null : KEY_FRAGMENT.matcher(message).replaceAll("[redacted]");
    }
}
