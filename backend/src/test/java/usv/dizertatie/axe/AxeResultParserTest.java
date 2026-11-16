package usv.dizertatie.axe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.Finding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AxeResultParser}.
 * Uses the fixture file {@code axe-sample.json} from test resources.
 */
class AxeResultParserTest {

    private AxeResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new AxeResultParser(new ObjectMapper());
    }

    private String loadFixture() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("axe-sample.json")) {
            assertThat(is).as("axe-sample.json fixture must be on the classpath").isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesFixtureIntoTwoFindings() throws IOException {
        String json = loadFixture();
        List<Finding> findings = parser.parse(json);
        assertThat(findings).hasSize(2);
    }

    @Test
    void firstFindingHasSourceMethodAXE() throws IOException {
        String json = loadFixture();
        Finding first = parser.parse(json).get(0);
        assertThat(first.getSourceMethod()).isEqualTo("AXE");
    }

    @Test
    void firstFindingSeverityIsCritical() throws IOException {
        String json = loadFixture();
        Finding first = parser.parse(json).get(0);
        assertThat(first.getSeverity()).isEqualTo("critical");
    }

    @Test
    void secondFindingSeverityIsSerious() throws IOException {
        String json = loadFixture();
        Finding second = parser.parse(json).get(1);
        assertThat(second.getSeverity()).isEqualTo("serious");
    }

    @Test
    void firstFindingWcagCriterionIs111() throws IOException {
        String json = loadFixture();
        Finding first = parser.parse(json).get(0);
        assertThat(first.getWcagCriterion()).isEqualTo("1.1.1");
    }

    @Test
    void firstFindingWcagLevelIsA() throws IOException {
        String json = loadFixture();
        Finding first = parser.parse(json).get(0);
        assertThat(first.getWcagLevel()).isEqualTo("A");
    }

    @Test
    void secondFindingWcagLevelIsAA() throws IOException {
        String json = loadFixture();
        Finding second = parser.parse(json).get(1);
        assertThat(second.getWcagLevel()).isEqualTo("AA");
    }

    @Test
    void firstFindingElementSelectorIsCssSelector() throws IOException {
        String json = loadFixture();
        Finding first = parser.parse(json).get(0);
        assertThat(first.getElementSelector()).isEqualTo("img.hero");
    }

    @Test
    void firstFindingDescriptionIsSet() throws IOException {
        String json = loadFixture();
        Finding first = parser.parse(json).get(0);
        assertThat(first.getDescription()).isEqualTo("Images must have alternate text");
    }

    @Test
    void parsesRuleIdsAndIncompleteItemsFromAxeRunResult() {
        String json = """
                {
                  "violations": [{
                    "id": "image-alt",
                    "impact": "critical",
                    "description": "Images need alternate text",
                    "tags": ["wcag111"],
                    "nodes": [{"target": ["img.hero"]}, {"target": ["img.logo"]}]
                  }],
                  "incomplete": [{
                    "id": "color-contrast",
                    "impact": "serious",
                    "description": "Contrast needs manual review",
                    "help": "Review contrast",
                    "helpUrl": "https://example.com/help",
                    "nodes": [{
                      "target": [".hero"],
                      "html": "<p class=\\\"hero\\\">Text</p>",
                      "failureSummary": "Check the background image manually"
                    }]
                  }]
                }
                """;

        List<Finding> findings = parser.parse(json);
        List<ManualReviewItem> manualReviewItems = parser.parseIncomplete(json);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::getRuleId).containsOnly("image-alt");
        assertThat(manualReviewItems).hasSize(1);
        assertThat(manualReviewItems.get(0).ruleId()).isEqualTo("color-contrast");
        assertThat(manualReviewItems.get(0).impact()).isEqualTo("serious");
        assertThat(manualReviewItems.get(0).elements()).singleElement()
                .satisfies(element -> {
                    assertThat(element.target()).isEqualTo(".hero");
                    assertThat(element.failureSummary()).isEqualTo("Check the background image manually");
                });
    }

    @Test
    void emptyJsonArrayProducesEmptyList() {
        List<Finding> findings = parser.parse("[]");
        assertThat(findings).isEmpty();
    }

    @Test
    void violationWithNoNodesProducesOneFindingWithEmptySelector() {
        String json = """
                [
                  {
                    "id": "html-lang-valid",
                    "impact": "minor",
                    "description": "HTML lang attribute must be valid",
                    "tags": ["wcag2a", "wcag311"],
                    "nodes": []
                  }
                ]
                """;
        List<Finding> findings = parser.parse(json);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getElementSelector()).isEmpty();
        assertThat(findings.get(0).getSeverity()).isEqualTo("minor");
    }

    @Test
    void violationWithNoWcagTagHasNoWcagCriterionButKeepsRuleId() {
        String json = """
                [
                  {
                    "id": "aria-required-attr",
                    "impact": "critical",
                    "description": "ARIA roles must have required attributes",
                    "tags": ["cat.aria"],
                    "nodes": []
                  }
                ]
                """;
        List<Finding> findings = parser.parse(json);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getWcagCriterion()).isNull();
        assertThat(findings.get(0).getRuleId()).isEqualTo("aria-required-attr");
    }

    @Test
    void parsesWcagCriteriaWithTwoDigitFinalComponents() {
        String json = """
                [
                  {"id":"target-size","impact":"serious","tags":["wcag2aa","wcag22aa","wcag222"],"nodes":[{"target":["a.small"]}]},
                  {"id":"reflow","impact":"serious","tags":["wcag2aa","wcag21aa","wcag2110"],"nodes":[{"target":["main"]}]}
                ]
                """;

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getWcagCriterion)
                .containsExactly("2.2.2", "2.1.10");
        assertThat(findings).extracting(Finding::getWcagLevel)
                .containsExactly("AA", "AA");
    }

    @Test
    void levelComesFromRegistry_forWcag22Criteria() throws Exception {
        usv.dizertatie.wcag.WcagCriterionRegistry registry = new usv.dizertatie.wcag.WcagCriterionRegistry();
        registry.load();
        AxeResultParser withRegistry = new AxeResultParser(new ObjectMapper(), registry);
        String json = """
                [{"id":"target-size","impact":"serious","description":"Touch targets",
                  "tags":["cat.sensory-and-visual-cues","wcag22aa","wcag258"],
                  "nodes":[{"target":["#btn"]}]}]
                """;

        Finding f = withRegistry.parse(json).get(0);

        assertThat(f.getWcagCriterion()).isEqualTo("2.5.8");
        assertThat(f.getWcagLevel()).isEqualTo("AA");
    }

    @Test
    void levelFallsBackToWcag21And22Tags_withoutRegistry() {
        String json = """
                [{"id":"x","impact":"serious","description":"d","tags":["wcag21aa","wcag1412"],
                  "nodes":[{"target":["p"]}]}]
                """;
        assertThat(parser.parse(json).get(0).getWcagLevel()).isEqualTo("AA");
    }

    @Test
    void wcag21And22LevelTags_copiedFromBundledAxe_areNotLabelledLevelA() {
        // Tag sets as they appear in the bundled axe-core 4.14.0 (issue #40).
        String json = """
                [{"id":"target-size","impact":"serious","tags":["cat.sensory-and-visual-cues","wcag22aa","wcag258"],"nodes":[{"target":["a"]}]},
                 {"id":"autocomplete-valid","impact":"serious","tags":["cat.forms","wcag21aa","wcag135"],"nodes":[{"target":["input"]}]},
                 {"id":"avoid-inline-spacing","impact":"serious","tags":["cat.structure","wcag21aa","wcag1412"],"nodes":[{"target":["p"]}]}]
                """;

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getWcagCriterion).containsExactly("2.5.8", "1.3.5", "1.4.12");
        assertThat(findings).extracting(Finding::getWcagLevel).containsExactly("AA", "AA", "AA");
    }

    /** #41: a rule that fails two success criteria reports both, once per element. */
    @Test
    void ruleWithTwoCriteria_yieldsOneFindingPerCriterionPerElement() {
        String json = """
                [{"id":"link-name","impact":"serious","description":"d",
                  "tags":["cat.name-role-value","wcag2a","wcag244","wcag412"],
                  "nodes":[{"target":["a.icon"]},{"target":["a.more"]}]}]""";

        List<Finding> findings = new AxeResultParser(new ObjectMapper()).parse(json);

        assertThat(findings).extracting(f -> f.getWcagCriterion() + " " + f.getElementSelector())
                .containsExactly("2.4.4 a.icon", "4.1.2 a.icon", "2.4.4 a.more", "4.1.2 a.more");
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.getRuleId()).isEqualTo("link-name");
            assertThat(f.getWcagLevel()).isEqualTo("A");
        });
    }
}
