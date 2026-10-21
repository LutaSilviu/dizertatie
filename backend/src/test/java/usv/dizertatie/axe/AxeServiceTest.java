package usv.dizertatie.axe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AxeServiceTest {

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
}
