package usv.dizertatie.axe;

import com.microsoft.playwright.Page;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring service that injects axe-core into a Playwright page and runs an accessibility audit.
 */
@Service
public class AxeService {

    public static final String AXE_VERSION = "4.14.0";

    /** WCAG 2.0–2.2 A/AA scope used for every completed axe scan. */
    public static final List<String> WCAG_TAGS = List.of(
            "wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");

    /**
     * Restricts the audit to WCAG 2.0–2.2 Level A and AA rules. Axe's best-practice rules
     * (e.g. "region") map to no WCAG success criterion, so they cannot be scored or
     * benchmarked against WCAG ground truth.
     */
    private static final String RUN_OPTIONS =
            "{ runOnly: { type: 'tag', values: ['" + String.join("', '", WCAG_TAGS) + "'] } }";

    private String axeScript;

    @PostConstruct
    void loadAxeScript() throws IOException {
        ClassPathResource resource = new ClassPathResource("axe/axe.min.js");
        axeScript = resource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Injects axe-core into the given page and runs an accessibility audit.
     *
     * @param page the Playwright page to audit
     * @return raw axe.run() result, including violations and incomplete items
     */
    public String run(Page page) {
        // addScriptTag evaluates in the page's global scope. Wrapping the source in
        // an arrow function body instead would scope axe-core's own `var axe` to that
        // function, leaving `axe` undefined by the time the audit call runs.
        page.addScriptTag(new Page.AddScriptTagOptions().setContent(axeScript));
        return (String) page.evaluate("async () => { const r = await axe.run(document, " + RUN_OPTIONS + "); return JSON.stringify({ violations: r.violations, incomplete: r.incomplete }); }");
    }
}
