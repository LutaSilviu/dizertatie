package usv.dizertatie.playwright;

import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageNavigatorTest {

    /** The shape Java Playwright actually produces for a failed navigation. */
    private static String playwrightDump(String netError, String url) {
        return "Error {\n"
                + "  message='net::" + netError + " at " + url + "\n"
                + "Call log:\n"
                + "  - navigating to \"" + url + "\", waiting until \"load\"\n"
                + "'\n"
                + "  name='Error'\n"
                + "  stack='Error: net::" + netError + " at " + url + "\n    at ...'\n"
                + "}";
    }

    @Test
    void nameNotResolved_isPlainEnglish() {
        String text = PageNavigator.describeBrowserError(playwrightDump("ERR_NAME_NOT_RESOLVED", "https://nope.invalid/"));
        assertThat(text).isEqualTo("The domain name could not be resolved — check the URL for typos (ERR_NAME_NOT_RESOLVED)");
    }

    @Test
    void commonNetworkErrors_areTranslated() {
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_CONNECTION_REFUSED", "http://localhost:1/")))
                .startsWith("The server refused the connection");
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_CONNECTION_TIMED_OUT", "https://a.example/")))
                .startsWith("The connection timed out");
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_CERT_AUTHORITY_INVALID", "https://a.example/")))
                .startsWith("The site's HTTPS certificate");
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_SSL_PROTOCOL_ERROR", "https://a.example/")))
                .startsWith("The site's HTTPS certificate");
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_ADDRESS_UNREACHABLE", "https://a.example/")))
                .startsWith("The server's address is unreachable");
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_INTERNET_DISCONNECTED", "https://a.example/")))
                .startsWith("The backend has no working internet connection");
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_ABORTED", "https://a.example/file.zip")))
                .startsWith("The page load was aborted");
    }

    @Test
    void unknownNetError_fallsBackToCleanedMessage() {
        assertThat(PageNavigator.describeBrowserError(playwrightDump("ERR_SOMETHING_NEW", "https://a.example/")))
                .isEqualTo("net::ERR_SOMETHING_NEW at https://a.example/");
    }

    @Test
    void plainMessage_isKeptToItsFirstLine() {
        assertThat(PageNavigator.describeBrowserError("Timeout 30000ms exceeded.\n=== logs ===")).isEqualTo("Timeout 30000ms exceeded.");
        assertThat(PageNavigator.describeBrowserError(null)).isEqualTo("unknown browser error");
        assertThat(PageNavigator.describeBrowserError("Error {\n}")).isEqualTo("unknown browser error");
    }

    @Test
    void loadFailure_buildsReadableMessage() {
        PageCaptureException e = PageNavigator.loadFailure("https://nope.invalid",
                new PlaywrightException(playwrightDump("ERR_NAME_NOT_RESOLVED", "https://nope.invalid/")));
        assertThat(e.getMessage()).isEqualTo("Could not load https://nope.invalid: "
                + "The domain name could not be resolved — check the URL for typos (ERR_NAME_NOT_RESOLVED)");

        PageCaptureException timeout = PageNavigator.loadFailure("https://slow.example",
                new TimeoutError("Timeout 30000ms exceeded.\nCall log:"));
        assertThat(timeout.getMessage()).isEqualTo(
                "Could not load https://slow.example: timed out while loading or capturing the page (Timeout 30000ms exceeded.)");
    }
}
