package ro.sluta.accessibility.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import com.deque.html.axecore.playwright.AxeBuilder;
import com.microsoft.playwright.Playwright;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationAccessibilitySmokeTest {
    @LocalServerPort private int port;

    @Test void mainPagePassesAxeSmokeAndReflowsWithVisibleKeyboardFocus() {
        try (Playwright playwright = Playwright.create()) {
            var browser = playwright.chromium().launch();
            var page = browser.newPage(new com.microsoft.playwright.Browser.NewPageOptions()
                    .setViewportSize(320, 800));
            page.navigate("http://127.0.0.1:" + port + "/");

            var results = new AxeBuilder(page).withTags(List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa")).analyze();
            assertThat(results.getViolations()).as("axe-core smoke violations").isEmpty();
            assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();

            page.keyboard().press("Tab");
            assertThat((Boolean) page.evaluate("document.activeElement !== document.body")).isTrue();
            assertThat((Boolean) page.evaluate("getComputedStyle(document.activeElement).outlineStyle !== 'none'")).isTrue();
            assertThat((Boolean) page.evaluate("[...document.querySelectorAll('button')].every(e => e.getBoundingClientRect().height >= 24 && e.getBoundingClientRect().width >= 24)")).isTrue();
            browser.close();
        }
    }
}
