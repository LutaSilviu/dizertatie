package usv.dizertatie.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Service;

/**
 * Spring service that captures the rendered HTML and a full-page screenshot of a web page
 * using a single Playwright browser session.
 */
@Service
public class PageCaptureService {

    private final BrowserType browserType;

    public PageCaptureService(BrowserType browserType) {
        this.browserType = browserType;
    }

    /**
     * Launches a headless browser, navigates to the given URL, waits for network idle,
     * and returns a {@link PageCapture} containing both the rendered HTML and a full-page
     * PNG screenshot — all captured in one browser session.
     *
     * @param url the URL to capture
     * @return a {@link PageCapture} with the rendered HTML and screenshot bytes
     * @throws PageCaptureException if the page cannot be reached or any error occurs
     */
    public PageCapture capture(String url) {
        Browser browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
        Page page = null;
        try {
            page = browser.newPage();
            PageNavigator.open(page, url);
            String html = page.content();
            byte[] screenshotPng = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            return new PageCapture(html, screenshotPng);
        } catch (PageBlockedException e) {
            throw e;
        } catch (Exception e) {
            throw new PageCaptureException("Failed to capture page from URL: " + url, e);
        } finally {
            if (page != null) {
                page.close();
            }
            browser.close();
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
