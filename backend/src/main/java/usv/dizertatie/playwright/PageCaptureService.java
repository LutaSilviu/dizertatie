package usv.dizertatie.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.springframework.stereotype.Service;

/**
 * Spring service that captures the rendered HTML and a full-page screenshot of a web page
     * using a single Playwright browser session. Navigation waits for DOM content and a bounded
     * stabilization period rather than waiting for global network idle.
 */
@Service
public class PageCaptureService {

    private final BrowserType browserType;

    public PageCaptureService(BrowserType browserType) {
        this.browserType = browserType;
    }

    /**
     * Launches a headless browser, navigates to the given URL,
     * and returns a {@link PageCapture} containing both the rendered HTML and a full-page
     * PNG screenshot — all captured in one browser session.
     *
     * @param url the URL to capture
     * @return a {@link PageCapture} with the rendered HTML and screenshot bytes
     * @throws PageCaptureException if the page cannot be reached or any error occurs
     */
    public PageCapture capture(String url) {
        // Playwright is not thread-safe: every use of the shared Playwright instance is serialised
        // (ScanService locks on the same BrowserType bean).
        synchronized (browserType) {
            Browser browser = null;
            Page page = null;
            try {
                browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
                page = browser.newPage();
                PageNavigator.open(page, url);
                String html = page.content();
                byte[] screenshotPng = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                return new PageCapture(html, screenshotPng);
            } catch (PageBlockedException e) {
                throw e;
            } catch (PlaywrightException e) {
                throw PageNavigator.loadFailure(url, e);
            } catch (Exception e) {
                throw new PageCaptureException("Failed to capture page from URL: " + url, e);
            } finally {
                closeQuietly(page, browser);
            }
        }
    }

    private static void closeQuietly(Page page, Browser browser) {
        try {
            if (page != null) {
                page.close();
            }
        } catch (RuntimeException ignored) {
            // closing the browser below disposes of the page anyway
        }
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (RuntimeException ignored) {
            // browser already gone
        }
    }

    /**
     * Launches a headless browser, navigates to the given URL, waits for network idle,
     * and returns the rendered outerHTML of the page.
     *
     * @param url the URL to capture
     * @return the rendered HTML string
     * @throws PageCaptureException if the page cannot be reached or any error occurs
     */
    public String captureHtml(String url) {
        return capture(url).html();
    }
}
