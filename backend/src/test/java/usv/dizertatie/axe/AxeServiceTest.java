package usv.dizertatie.axe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AxeServiceTest {

    @Test
    void embeddedEngineVersionAndConfiguredWcagTagsMatchScanMetadata() throws Exception {
        String engine = new ClassPathResource("axe/axe.min.js")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(engine).contains("axe.version=\"" + AxeService.AXE_VERSION + "\"");
        assertThat(AxeService.AXE_VERSION).isEqualTo("4.14.0");
        assertThat(AxeService.WCAG_TAGS)
                .containsExactly("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
    }

    @Test
    void run_reportsImageWithoutAltTextAsViolation() throws Exception {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.setContent("""
                    <!doctype html>
                    <html lang="en">
                      <head><title>Accessibility fixture</title></head>
                      <body><img src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs="></body>
                    </html>
                    """);

            AxeService axeService = new AxeService();
            axeService.loadAxeScript();
            JsonNode violations = new ObjectMapper().readTree(axeService.run(page));

            assertThat(violations.findValuesAsText("id")).contains("image-alt");
        }
    }

    @Test
    void run_reportsOnlyWcagLevelAAndAaRules() throws Exception {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            // The paragraph outside any landmark trips axe's best-practice "region" rule,
            // which has no WCAG success criterion behind it.
            page.setContent("""
                    <!doctype html>
                    <html lang="en">
                      <head><title>Accessibility fixture</title></head>
                      <body>
                        <p>Content outside any landmark</p>
                        <img src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=">
                      </body>
                    </html>
                    """);

            AxeService axeService = new AxeService();
            axeService.loadAxeScript();
            JsonNode violations = new ObjectMapper().readTree(axeService.run(page));

            assertThat(violations.findValuesAsText("id"))
                    .contains("image-alt")
                    .doesNotContain("region");
        }
    }
}
