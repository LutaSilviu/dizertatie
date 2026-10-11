package usv.dizertatie.playwright;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class PlaywrightConfig {

    @Bean
    @Lazy
    public BrowserType chromiumBrowserType() {
        Playwright playwright = Playwright.create();
        return playwright.chromium();
    }
}
