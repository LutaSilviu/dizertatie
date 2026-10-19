package usv.dizertatie.axe;

import com.microsoft.playwright.Page;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring service that injects axe-core into a Playwright page and runs an accessibility audit.
 */
@Service
public class AxeService {

    public static final String AXE_VERSION = "4.9.0";

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
     * @return raw violations JSON string from axe.run()
     */
    public String run(Page page) {
        // addScriptTag evaluates in the page's global scope. Wrapping the source in
        // an arrow function body instead would scope axe-core's own `var axe` to that
        // function, leaving `axe` undefined by the time the audit call runs.
        page.addScriptTag(new Page.AddScriptTagOptions().setContent(axeScript));
        return (String) page.evaluate("async () => { const r = await axe.run(); return JSON.stringify(r.violations); }");
    }
}
