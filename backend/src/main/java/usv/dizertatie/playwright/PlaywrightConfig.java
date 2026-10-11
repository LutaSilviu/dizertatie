package usv.dizertatie.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaywrightConfig {

    private Playwright playwright;
    private Browser browser;

    @Bean
    public BrowserType chromium() {
        playwright = Playwright.create();
        return playwright.chromium();
    }

    @Bean
    public Browser browser(BrowserType chromium) {
        browser = chromium.launch(new BrowserType.LaunchOptions().setHeadless(true));
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
