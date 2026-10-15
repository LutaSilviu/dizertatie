package usv.dizertatie.ai;

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
 * Additional edge-case unit tests for {@link AiResponseParser} using fixture files.
 *
 * <p>These tests complement the basic tests in {@link AiResponseParserTest} by exercising:
 * <ul>
 *   <li>Fixture-based assertions covering three scenarios: well-formed, partial, malformed</li>
 *   <li>Exact wcagCriterion value preservation (e.g. "1.1.1", "2.4.1")</li>
 *   <li>All four severity values: critical, serious, moderate, minor</li>
 *   <li>All three wcagLevel values: A, AA, AAA</li>
 *   <li>elementSelector preservation</li>
 *   <li>Confidence boundary values (0.0 and 1.0)</li>
 *   <li>Multiple findings all receiving sourceMethod="AI"</li>
 * </ul>
 */
class AiResponseParserEdgeCaseTest {

    private AiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiResponseParser(new ObjectMapper());
    }

    // -----------------------------------------------------------------------
    // Fixture: well-formed (3 findings, all fields present)
    // -----------------------------------------------------------------------

    @Test
    void wellFormedFixtureReturnThreeFindings() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings).hasSize(3);
    }

    @Test
    void wellFormedFixtureAllFindingsHaveSourceMethodAI() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getSourceMethod)
                .containsOnly("AI");
    }

    @Test
    void wellFormedFixtureFirstFindingHasCorrectWcagCriterion() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        Finding first = parser.parse(json).get(0);

        // Exact value "1.1.1" must be preserved as-is
        assertThat(first.getWcagCriterion()).isEqualTo("1.1.1");
    }

    @Test
    void wellFormedFixtureSecondFindingHasCorrectWcagCriterion() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        Finding second = parser.parse(json).get(1);

        // Exact value "2.4.1" must be preserved as-is
        assertThat(second.getWcagCriterion()).isEqualTo("2.4.1");
    }

    @Test
    void wellFormedFixtureSeverityValuesCriticalSeriousModerate() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getSeverity)
                .containsExactly("critical", "serious", "moderate");
    }

    @Test
    void wellFormedFixtureWcagLevelValuesAAndAAAndAAA() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getWcagLevel)
                .containsExactly("A", "AA", "AAA");
    }

    @Test
    void wellFormedFixtureElementSelectorsPreservedExactly() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getElementSelector)
                .containsExactly("img.hero", "nav.main-nav", "p.body-text");
    }

    @Test
    void wellFormedFixtureConfidenceValuesPreserved() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings.get(0).getConfidence()).isEqualTo(0.97);
        assertThat(findings.get(1).getConfidence()).isEqualTo(0.85);
        assertThat(findings.get(2).getConfidence()).isEqualTo(0.73);
    }

    @Test
    void wellFormedFixtureSuggestionFieldsPreserved() throws IOException {
        String json = loadFixture("fixtures/ai-response-well-formed.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings.get(0).getSuggestion())
                .isEqualTo("Add a descriptive alt attribute to the image element");
        assertThat(findings.get(1).getSuggestion())
                .isEqualTo("Add a skip navigation link before the main navigation");
        assertThat(findings.get(2).getSuggestion())
                .isEqualTo("Increase text contrast ratio to at least 7:1 against background");
    }

    // -----------------------------------------------------------------------
    // Fixture: partial (some findings missing optional fields)
    // -----------------------------------------------------------------------

    @Test
    void partialFixtureReturnsTwoFindings() throws IOException {
        String json = loadFixture("fixtures/ai-response-partial.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings).hasSize(2);
    }

    @Test
    void partialFixtureFirstFindingHasNullSuggestionAndNullConfidence() throws IOException {
        String json = loadFixture("fixtures/ai-response-partial.json");

        Finding first = parser.parse(json).get(0);

        // suggestion and confidence are absent from the fixture — must map to null without error
        assertThat(first.getSuggestion()).isNull();
        assertThat(first.getConfidence()).isNull();
    }

    @Test
    void partialFixtureSecondFindingHasSuggestionButNullConfidence() throws IOException {
        String json = loadFixture("fixtures/ai-response-partial.json");

        Finding second = parser.parse(json).get(1);

        // suggestion is present, confidence is explicitly null in the fixture
        assertThat(second.getSuggestion())
                .isEqualTo("Ensure keyboard focus is visible and all interactive elements are reachable");
        assertThat(second.getConfidence()).isNull();
    }

    @Test
    void partialFixtureSeverityValueMinorIsPreserved() throws IOException {
        String json = loadFixture("fixtures/ai-response-partial.json");

        Finding first = parser.parse(json).get(0);

        // Verify the "minor" severity value maps correctly
        assertThat(first.getSeverity()).isEqualTo("minor");
    }

    @Test
    void partialFixtureAllFindingsHaveSourceMethodAI() throws IOException {
        String json = loadFixture("fixtures/ai-response-partial.json");

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getSourceMethod)
                .containsOnly("AI");
    }

    // -----------------------------------------------------------------------
    // Fixture: malformed JSON
    // -----------------------------------------------------------------------

    @Test
    void malformedFixtureReturnsEmptyListWithoutException() throws IOException {
        String json = loadFixture("fixtures/ai-response-malformed.json");

        // Must not throw; must return an empty list
        List<Finding> findings = parser.parse(json);

        assertThat(findings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Confidence boundary-value tests (inline, no fixture needed)
    // -----------------------------------------------------------------------

    @Test
    void confidenceAtMinimumBoundaryZeroIsPreserved() {
        String json = """
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img",
                      "description": "Missing alt text",
                      "confidence": 0.0
                    }
                  ]
                }
                """;

        Finding finding = parser.parse(json).get(0);

        assertThat(finding.getConfidence()).isEqualTo(0.0);
    }

    @Test
    void confidenceAtMaximumBoundaryOneIsPreserved() {
        String json = """
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img",
                      "description": "Missing alt text",
                      "confidence": 1.0
                    }
                  ]
                }
                """;

        Finding finding = parser.parse(json).get(0);

        assertThat(finding.getConfidence()).isEqualTo(1.0);
    }

    // -----------------------------------------------------------------------
    // Multiple findings — each gets sourceMethod="AI"
    // -----------------------------------------------------------------------

    @Test
    void multipleFindingsEachGetSourceMethodAI() {
        String json = """
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img",
                      "description": "Missing alt text"
                    },
                    {
                      "wcagCriterion": "2.4.1",
                      "wcagLevel": "A",
                      "severity": "serious",
                      "elementSelector": "nav",
                      "description": "No skip link"
                    },
                    {
                      "wcagCriterion": "1.4.3",
                      "wcagLevel": "AA",
                      "severity": "moderate",
                      "elementSelector": "p",
                      "description": "Low contrast"
                    },
                    {
                      "wcagCriterion": "2.4.6",
                      "wcagLevel": "AA",
                      "severity": "minor",
                      "elementSelector": "h2",
                      "description": "Heading not descriptive"
                    }
                  ]
                }
                """;

        List<Finding> findings = parser.parse(json);

        assertThat(findings).hasSize(4);
        assertThat(findings).extracting(Finding::getSourceMethod)
                .containsOnly("AI");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private String loadFixture(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Test fixture not found on classpath: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
