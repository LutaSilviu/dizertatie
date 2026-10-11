package usv.dizertatie.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Service;

/**
 * Spring service that captures the rendered HTML of a web page using Playwright.
 */
@Service
public class PageCaptureService {

    private final BrowserType browserType;

    public PageCaptureService(BrowserType browserType) {
        this.browserType = browserType;
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
        Browser browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
        Page page = null;
        try {
            page = browser.newPage();
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            return page.content();
        } catch (Exception e) {
            throw new PageCaptureException("Failed to capture HTML from URL: " + url, e);
        } finally {
            if (page != null) {
                page.close();
            }
            browser.close();
        }
    }
}
