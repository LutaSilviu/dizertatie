package ro.sluta.accessibility.browser;

import com.microsoft.playwright.Page;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class PageStabilizer {
    public void stabilize(Page page, Duration fontTimeout, Duration finalDelay) {
        page.evaluate("timeout => Promise.race([" +
                "document.fonts ? document.fonts.ready : Promise.resolve()," +
                "new Promise(resolve => setTimeout(resolve, timeout))])", (double) fontTimeout.toMillis());
        page.evaluate("() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))");
        page.waitForTimeout(finalDelay.toMillis());
    }
}
