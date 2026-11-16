package usv.dizertatie.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import usv.dizertatie.axe.AxeService;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.playwright.PageNavigator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ties AI findings to real elements of the scanned page.
 *
 * <p>A model writes selectors in its own style and often one selector for a group of elements
 * ({@code "#nav img:not([alt])"}). Such a string can never equal the selector axe-core or the
 * ground-truth dataset uses for the same element, so the finding could neither be merged with an
 * axe finding nor matched to a label, even when it points at the right element.
 *
 * <p>Grounding resolves each AI selector in the live DOM and emits one finding per matched
 * element, named with {@code axe.utils.getSelector}: the generator axe uses for its own targets
 * and the one the dataset labels were named with. It changes only how an element is named, never
 * which elements the model claimed. A selector that matches nothing (or is not valid CSS) is kept
 * as the model wrote it, so an unverifiable claim stays visible instead of disappearing.
 */
@Service
public class AiFindingGrounder {

    private static final Logger log = LoggerFactory.getLogger(AiFindingGrounder.class);

    /** Upper bound on elements one AI finding may expand to; guards against selectors like {@code "*"}. */
    static final int MAX_ELEMENTS_PER_FINDING = 100;

    private static final String RESOLVE_SCRIPT = """
            (selectors) => {
              try { axe.setup(document); } catch (e) { /* already set up */ }
              const out = selectors.map(sel => {
                if (!sel) return [];
                let nodes;
                try { nodes = Array.from(document.querySelectorAll(sel)); } catch (e) { return []; }
                return nodes.slice(0, %d).map(n => {
                  try { return axe.utils.getSelector(n); } catch (e) { return null; }
                }).filter(s => !!s);
              });
              try { axe.teardown(); } catch (e) { /* nothing to tear down */ }
              return JSON.stringify(out);
            }
            """.formatted(MAX_ELEMENTS_PER_FINDING);

    private final BrowserType browserType;
    private final AxeService axeService;
    private final ObjectMapper objectMapper;

    public AiFindingGrounder(BrowserType browserType, AxeService axeService, ObjectMapper objectMapper) {
        this.browserType = browserType;
        this.axeService = axeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Opens {@code url} and grounds the findings in its DOM. Grounding is a naming step: if the
     * page cannot be opened again the findings are returned as the model wrote them.
     */
    public List<Finding> ground(String url, List<Finding> aiFindings) {
        if (aiFindings == null || aiFindings.isEmpty()) {
            return aiFindings == null ? List.of() : aiFindings;
        }
        // Playwright is not thread-safe: every use of the shared instance is serialised on this bean.
        synchronized (browserType) {
            Browser browser = null;
            try {
                browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
                Page page = browser.newContext(new Browser.NewContextOptions().setBypassCSP(true)).newPage();
                PageNavigator.open(page, url);
                return ground(page, aiFindings);
            } catch (RuntimeException e) {
                log.warn("Could not ground AI findings for url={}: {}", url, e.toString());
                return aiFindings;
            } finally {
                if (browser != null) {
                    try {
                        browser.close();
                    } catch (RuntimeException ignored) {
                        // browser already gone
                    }
                }
            }
        }
    }

    /** Grounds the findings in an already opened page. */
    public List<Finding> ground(Page page, List<Finding> aiFindings) {
        axeService.inject(page);
        List<String> selectors = aiFindings.stream()
                .map(f -> f.getElementSelector() == null ? "" : f.getElementSelector())
                .toList();
        List<List<String>> resolved;
        try {
            resolved = objectMapper.readValue((String) page.evaluate(RESOLVE_SCRIPT, selectors),
                    new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Selector resolution returned an unexpected result", e);
        }
        return expand(aiFindings, resolved);
    }

    /**
     * Replaces each finding by one finding per resolved element. Findings that resolve to the same
     * criterion and element are reported once.
     */
    static List<Finding> expand(List<Finding> aiFindings, List<List<String>> resolved) {
        List<Finding> grounded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < aiFindings.size(); i++) {
            Finding finding = aiFindings.get(i);
            List<String> elements = i < resolved.size() && resolved.get(i) != null ? resolved.get(i) : List.of();
            if (elements.isEmpty()) {
                if (seen.add(finding.getWcagCriterion() + "|" + finding.getElementSelector())) {
                    grounded.add(finding);
                }
                continue;
            }
            for (String selector : elements) {
                if (seen.add(finding.getWcagCriterion() + "|" + selector)) {
                    grounded.add(Finding.builder()
                            .scan(finding.getScan())
                            .sourceMethod(finding.getSourceMethod())
                            .wcagCriterion(finding.getWcagCriterion())
                            .wcagLevel(finding.getWcagLevel())
                            .ruleId(finding.getRuleId())
                            .severity(finding.getSeverity())
                            .elementSelector(selector)
                            .description(finding.getDescription())
                            .suggestion(finding.getSuggestion())
                            .confidence(finding.getConfidence())
                            .build());
                }
            }
        }
        return grounded;
    }
}
