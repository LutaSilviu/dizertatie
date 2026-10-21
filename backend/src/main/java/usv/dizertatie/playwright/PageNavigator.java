package usv.dizertatie.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Navigates a Playwright page and refuses pages the target site did not actually serve.
 *
 * <p>Sites behind bot protection (e.g. Cloudflare) answer headless browsers with a challenge
 * page. Auditing that page instead of the real one silently produces bogus findings, so every
 * scan path must navigate through here.
 */
public final class PageNavigator {

    private PageNavigator() {
    }

    /**
     * Navigates to the URL, waits for network idle, and verifies the main document response.
     *
     * @throws PageBlockedException if the site answered with a bot challenge or an HTTP error
     */
    public static void open(Page page, String url) {
        Response response = page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        if (response == null) {
            return;
        }
        if (response.headers().containsKey("cf-mitigated")) {
            throw new PageBlockedException("Target site served a bot challenge instead of the page: " + url);
        }
        if (response.status() >= 400) {
            throw new PageBlockedException("Target site responded with HTTP " + response.status() + ": " + url);
        }
    }
}
