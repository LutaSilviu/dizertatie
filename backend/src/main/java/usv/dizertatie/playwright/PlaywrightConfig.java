package usv.dizertatie.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class PlaywrightConfig {

    private Playwright playwright;
    private Browser browser;

    @Bean
    @Lazy
    public BrowserType chromiumBrowserType() {
        playwright = Playwright.create();
        return playwright.chromium();
    }

    @Bean
    @Lazy
    public Browser browser(BrowserType chromiumBrowserType) {
        browser = chromiumBrowserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
        return browser;
    }

    @PreDestroy
    public void close() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
