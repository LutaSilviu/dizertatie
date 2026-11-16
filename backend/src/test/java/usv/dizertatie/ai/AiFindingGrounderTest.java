package usv.dizertatie.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import usv.dizertatie.axe.AxeResultParser;
import usv.dizertatie.axe.AxeService;
import usv.dizertatie.domain.Finding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiFindingGrounderTest {

    private static final String HTML = """
            <html lang="en"><head><title>t</title></head><body>
              <div id="nav"><img src="a.gif"><img src="b.gif"><img src="c.gif" alt="ok"></div>
              <p class="headline">News</p>
            </body></html>
            """;

    private static Playwright playwright;
    private static Browser browser;
    private static AxeService axeService;
    private static AiFindingGrounder grounder;

    @BeforeAll
    static void start() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        axeService = new AxeService();
        ReflectionTestUtils.invokeMethod(axeService, "loadAxeScript");
        grounder = new AiFindingGrounder(playwright.chromium(), axeService, new ObjectMapper());
    }

    @AfterAll
    static void stop() {
        playwright.close();
    }

    private static Finding ai(String criterion, String selector) {
        return Finding.builder().sourceMethod("AI").wcagCriterion(criterion).wcagLevel("A")
                .severity("serious").elementSelector(selector).description("d").suggestion("s")
                .confidence(0.9).build();
    }

    @Test
    void groupSelector_becomesOneFindingPerElement_namedExactlyLikeAxeNamesThem() {
        try (Page page = browser.newPage()) {
            page.setContent(HTML);

            List<Finding> grounded = grounder.ground(page, List.of(ai("1.1.1", "#nav img:not([alt])")));

            List<String> axeTargets = new AxeResultParser(new ObjectMapper()).parse(axeService.run(page)).stream()
                    .filter(f -> "image-alt".equals(f.getRuleId()))
                    .map(Finding::getElementSelector)
                    .toList();
            assertThat(axeTargets).hasSize(2);
            assertThat(grounded).extracting(Finding::getElementSelector)
                    .containsExactlyInAnyOrderElementsOf(axeTargets);
            assertThat(grounded).allSatisfy(f -> {
                assertThat(f.getSourceMethod()).isEqualTo("AI");
                assertThat(f.getWcagCriterion()).isEqualTo("1.1.1");
                assertThat(f.getDescription()).isEqualTo("d");
                assertThat(f.getConfidence()).isEqualTo(0.9);
            });
        }
    }

    @Test
    void selectorThatMatchesNothingOrIsInvalid_isKeptAsWritten() {
        try (Page page = browser.newPage()) {
            page.setContent(HTML);

            List<Finding> grounded = grounder.ground(page,
                    List.of(ai("1.3.1", "#does-not-exist"), ai("2.4.4", "a[[broken"), ai("1.3.1", null)));

            assertThat(grounded).extracting(Finding::getElementSelector)
                    .containsExactly("#does-not-exist", "a[[broken", null);
        }
    }

    @Test
    void findingsResolvingToTheSameCriterionAndElement_areReportedOnce() {
        try (Page page = browser.newPage()) {
            page.setContent(HTML);

            List<Finding> grounded = grounder.ground(page,
                    List.of(ai("1.3.1", "p.headline"), ai("1.3.1", "body > p"), ai("2.4.6", "p")));

            assertThat(grounded).hasSize(2);
            assertThat(grounded).extracting(Finding::getWcagCriterion).containsExactly("1.3.1", "2.4.6");
            assertThat(grounded.get(0).getElementSelector()).isEqualTo(grounded.get(1).getElementSelector());
        }
    }

    @Test
    void pageThatCannotBeOpened_leavesFindingsUntouched() {
        List<Finding> findings = List.of(ai("1.1.1", "img"));

        assertThat(grounder.ground("http://127.0.0.1:1/", findings)).isSameAs(findings);
    }

    @Test
    void noFindings_doesNotOpenABrowser() {
        assertThat(grounder.ground("http://127.0.0.1:1/", List.of())).isEmpty();
    }
}
