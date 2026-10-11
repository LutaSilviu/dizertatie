package usv.dizertatie.axe;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Service;

/**
 * Spring service that injects the axe-core accessibility library into a Playwright page
 * and runs an accessibility scan, returning the raw violations JSON.
 */
@Service
public class AxeService {

    public static final String AXE_VERSION = "4.9.1";

    private static final String AXE_CDN_URL =
            "https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.9.1/axe.min.js";

    /**
     * Injects axe-core into the page (if not already present) and runs an accessibility scan.
     *
     * @param page a Playwright {@link Page} that has already navigated to the target URL
     * @return the raw JSON string of axe violations (array)
     */
    public String run(Page page) {
        // Inject axe-core from CDN if not already present
        page.evaluate("""
                if (typeof axe === 'undefined') {
                    var script = document.createElement('script');
                    script.src = '%s';
                    document.head.appendChild(script);
                }
                """.formatted(AXE_CDN_URL));

        // Wait until axe is available
        page.waitForFunction("typeof axe !== 'undefined'");

        // Run axe and return violations as JSON string
        Object result = page.evaluate("""
                axe.run().then(results => JSON.stringify(results.violations))
                """);

        return result != null ? result.toString() : "[]";
    }
}
