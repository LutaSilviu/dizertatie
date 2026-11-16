package usv.dizertatie.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Navigates a Playwright page and refuses pages the target site did not actually serve.
 *
 * <p>Sites behind bot protection (e.g. Cloudflare) answer headless browsers with a challenge
 * page. Auditing that page instead of the real one silently produces bogus findings, so every
 * scan path must navigate through here.
 */
public final class PageNavigator {

    private static final Logger log = LoggerFactory.getLogger(PageNavigator.class);

    /**
     * Hard limit: the main document must arrive and be parsed ({@code DOMContentLoaded}) within
     * this time, otherwise there is nothing to audit and the scan fails.
     */
    static final double NAVIGATION_TIMEOUT_MS = 15_000;

    /**
     * Soft limit for the {@code load} event. One hanging sub-resource (an analytics pixel, a slow
     * image or font) keeps {@code load} from ever firing; the DOM is complete anyway, so after
     * this grace period the page is audited as rendered.
     */
    static final double LOAD_GRACE_MS = 10_000;

    /**
     * Soft limit for network idle after {@code load}, for late client-side rendering. Many real
     * sites never go idle (beacons, websockets, polling), so this is best effort as well.
     */
    static final double NETWORK_IDLE_GRACE_MS = 5_000;

    private PageNavigator() {
    }

    /**
     * Navigates to the URL and verifies the main document response. Only {@code DOMContentLoaded}
     * is required; {@code load} and network idle are waited for briefly and their timeouts ignored,
     * so a page that never goes idle is still audited as rendered.
     *
     * @throws PageBlockedException     if the target explicitly blocks access or serves a bot challenge
     * @throws RateLimitedPageException if the target responds with HTTP 429
     * @throws TimeoutError             if {@code DOMContentLoaded} does not fire in time
     */
    public static void open(Page page, String url) {
        open(page, url, NAVIGATION_TIMEOUT_MS, LOAD_GRACE_MS, NETWORK_IDLE_GRACE_MS);
    }

    static void open(Page page, String url, double navigationTimeoutMs, double loadGraceMs, double idleGraceMs) {
        Response response = page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(navigationTimeoutMs));
        if (response == null) {
            throw new RuntimeException("Target did not provide a main document response.");
        }
        if (response.headers().containsKey("cf-mitigated")) {
            throw new PageBlockedException("Target site served a bot challenge instead of the page.");
        }
        if (response.status() == 429) {
            throw new RateLimitedPageException("Target site responded with HTTP 429.");
        }
        if (response.status() == 401 || response.status() == 403) {
            throw new PageBlockedException("Target site blocked access with HTTP " + response.status() + ".");
        }
        if (response.status() >= 400) {
            throw new RuntimeException("Target site responded with HTTP " + response.status() + ".");
        }
        if (!waitSoftly(page, LoadState.LOAD, loadGraceMs)) {
            log.info("{}: load event did not fire within {} ms; auditing the page as rendered", url, (long) loadGraceMs);
            return; // a resource is hanging, so the network will not go idle either
        }
        waitSoftly(page, LoadState.NETWORKIDLE, idleGraceMs);
    }

    /** Waits for the load state; returns {@code false} instead of failing when the grace period expires. */
    private static boolean waitSoftly(Page page, LoadState state, double timeoutMs) {
        try {
            page.waitForLoadState(state, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    /**
     * Converts a Playwright failure (DNS error, refused connection, timeout, crashed page) into a
     * {@link PageCaptureException} whose message a user can read. The raw message is logged.
     */
    public static PageCaptureException loadFailure(String url, PlaywrightException e) {
        log.warn("Browser failed on {}: {}", url, e.getMessage());
        String reason = e instanceof TimeoutError
                ? "timed out while loading or capturing the page (" + describeBrowserError(e.getMessage()) + ")"
                : describeBrowserError(e.getMessage());
        return new PageCaptureException("Could not load " + url + ": " + reason, e);
    }

    /** {@code message='net::ERR_NAME_NOT_RESOLVED at https://...'} inside Playwright's "Error { ... }" dump. */
    private static final Pattern EMBEDDED_MESSAGE = Pattern.compile("message='([^\\n']*)");
    private static final Pattern NET_ERROR = Pattern.compile("net::(ERR_[A-Z0-9_]+)");

    /**
     * Extracts the useful part of a Playwright error message (Java Playwright wraps it as
     * {@code Error { message='...' name='...' stack='...' }}) and translates Chromium
     * {@code net::ERR_*} codes into plain English.
     */
    public static String describeBrowserError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown browser error";
        }
        Matcher embedded = EMBEDDED_MESSAGE.matcher(raw);
        String message = embedded.find() ? embedded.group(1).strip() : firstLine(raw);
        Matcher net = NET_ERROR.matcher(message.isEmpty() ? raw : message);
        if (net.find()) {
            String plain = netErrorText(net.group(1));
            if (plain != null) {
                return plain + " (" + net.group(1) + ")";
            }
        }
        return message.isEmpty() || message.equals("Error {") ? "unknown browser error" : message;
    }

    private static String netErrorText(String code) {
        if (code.startsWith("ERR_CERT_") || code.startsWith("ERR_SSL_") || code.equals("ERR_BAD_SSL_CLIENT_AUTH_CERT")) {
            return "The site's HTTPS certificate or TLS setup is invalid or not trusted";
        }
        return switch (code) {
            case "ERR_NAME_NOT_RESOLVED", "ERR_NAME_RESOLUTION_FAILED" ->
                    "The domain name could not be resolved — check the URL for typos";
            case "ERR_CONNECTION_REFUSED" -> "The server refused the connection — the site may be down";
            case "ERR_CONNECTION_TIMED_OUT", "ERR_TIMED_OUT" -> "The connection timed out — the server did not respond";
            case "ERR_CONNECTION_RESET", "ERR_CONNECTION_CLOSED", "ERR_EMPTY_RESPONSE" ->
                    "The server closed the connection unexpectedly";
            case "ERR_ADDRESS_UNREACHABLE" -> "The server's address is unreachable from the backend";
            case "ERR_INTERNET_DISCONNECTED", "ERR_NETWORK_CHANGED" -> "The backend has no working internet connection";
            case "ERR_ABORTED" -> "The page load was aborted (the URL may start a download or redirect away)";
            case "ERR_TOO_MANY_REDIRECTS" -> "The site redirected too many times";
            case "ERR_INVALID_URL", "ERR_UNSAFE_PORT", "ERR_DISALLOWED_URL_SCHEME" -> "The URL cannot be opened by the browser";
            case "ERR_PROXY_CONNECTION_FAILED", "ERR_TUNNEL_CONNECTION_FAILED" -> "Could not connect through the network proxy";
            case "ERR_BLOCKED_BY_CLIENT", "ERR_BLOCKED_BY_RESPONSE" -> "The page load was blocked";
            default -> null;
        };
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.strip();
        int nl = trimmed.indexOf('\n');
        return nl < 0 ? trimmed : trimmed.substring(0, nl).strip();
    }
}
