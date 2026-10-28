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
     * Navigates after DOM content is loaded, allows a short bounded stabilization period, and
     * verifies the main document response. It intentionally does not wait for global network idle.
     *
     * @throws PageBlockedException if the target explicitly blocks access or serves a bot challenge
     * @throws RateLimitedPageException if the target responds with HTTP 429
     */
    public static void open(Page page, String url) {
        Response response = page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(15_000));
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

        page.waitForTimeout(500);
        page.waitForFunction("() => document.readyState === 'interactive' || document.readyState === 'complete'",
                null, new Page.WaitForFunctionOptions().setTimeout(2_000));
    }
}
