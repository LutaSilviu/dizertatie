package usv.dizertatie.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.Finding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiResponseParser}.
 */
class AiResponseParserTest {

    private AiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiResponseParser(new ObjectMapper());
    }

    // -----------------------------------------------------------------------
    // Happy-path tests
    // -----------------------------------------------------------------------

    @Test
    void parsesValidJsonWithTwoFindings() {
        String json = """
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img.hero",
                      "description": "Images must have alternate text",
                      "suggestion": "Add an alt attribute",
                      "confidence": 0.95
                    },
                    {
                      "wcagCriterion": "1.4.3",
                      "wcagLevel": "AA",
                      "severity": "serious",
                      "elementSelector": "p.small",
                      "description": "Text must have sufficient colour contrast",
                      "suggestion": "Increase contrast ratio to at least 4.5:1",
                      "confidence": 0.88
                    }
                  ]
                }
                """;

        List<Finding> findings = parser.parse(json);

        assertThat(findings).hasSize(2);
    }

    @Test
    void allFindingsHaveSourceMethodAI() {
        String json = """
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img.hero",
                      "description": "Images must have alternate text"
                    },
                    {
                      "wcagCriterion": "1.4.3",
                      "wcagLevel": "AA",
                      "severity": "serious",
                      "elementSelector": "p.small",
                      "description": "Insufficient contrast"
                    }
                  ]
                }
                """;

        List<Finding> findings = parser.parse(json);

        assertThat(findings).extracting(Finding::getSourceMethod)
                .containsOnly("AI");
    }

    @Test
    void fieldsMappedCorrectlyOnFirstFinding() {
        String json = """
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img.hero",
                      "description": "Images must have alternate text",
                      "suggestion": "Add an alt attribute",
                      "confidence": 0.95
                    }
                  ]
                }
                """;

        Finding finding = parser.parse(json).get(0);

        assertThat(finding.getWcagCriterion()).isEqualTo("1.1.1");
        assertThat(finding.getWcagLevel()).isEqualTo("A");
        assertThat(finding.getSeverity()).isEqualTo("critical");
        assertThat(finding.getElementSelector()).isEqualTo("img.hero");
        assertThat(finding.getDescription()).isEqualTo("Images must have alternate text");
        assertThat(finding.getSuggestion()).isEqualTo("Add an alt attribute");
        assertThat(finding.getConfidence()).isEqualTo(0.95);
    }

    // -----------------------------------------------------------------------
    // Optional-field tests
    // -----------------------------------------------------------------------

    @Test
    void missingOptionalFieldsSuggestionAndConfidenceAreNull() {
        String json = """
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img.hero",
                      "description": "Images must have alternate text"
                    }
                  ]
                }
                """;

        Finding finding = parser.parse(json).get(0);

        assertThat(finding.getSuggestion()).isNull();
        assertThat(finding.getConfidence()).isNull();
    }

    // -----------------------------------------------------------------------
    // Empty / null input tests
    // -----------------------------------------------------------------------

    @Test
    void emptyFindingsArrayReturnsEmptyList() {
        String json = """
                {
                  "findings": []
                }
                """;

        List<Finding> findings = parser.parse(json);

        assertThat(findings).isEmpty();
    }

    @Test
    void nullInputReturnsEmptyList() {
        List<Finding> findings = parser.parse(null);

        assertThat(findings).isEmpty();
    }

    @Test
    void blankInputReturnsEmptyList() {
        List<Finding> findings = parser.parse("   ");

        assertThat(findings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Error-handling tests
    // -----------------------------------------------------------------------

    @Test
    void malformedJsonReturnsEmptyList() {
        List<Finding> findings = parser.parse("{ this is not valid json }");

        assertThat(findings).isEmpty();
    }

    @Test
    void jsonWithoutFindingsKeyReturnsEmptyList() {
        String json = """
                {
                  "results": []
                }
                """;

        List<Finding> findings = parser.parse(json);

        assertThat(findings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Markdown code-fence tests
    // -----------------------------------------------------------------------

    @Test
    void jsonWrappedInMarkdownFencesIsParsedCorrectly() {
        String json = """
                ```json
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.1.1",
                      "wcagLevel": "A",
                      "severity": "critical",
                      "elementSelector": "img.hero",
                      "description": "Images must have alternate text"
                    }
                  ]
                }
                ```""";

        List<Finding> findings = parser.parse(json);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getSourceMethod()).isEqualTo("AI");
        assertThat(findings.get(0).getWcagCriterion()).isEqualTo("1.1.1");
    }

    @Test
    void jsonWrappedInPlainCodeFencesIsParsedCorrectly() {
        String json = """
                ```
                {
                  "findings": [
                    {
                      "wcagCriterion": "1.4.3",
                      "wcagLevel": "AA",
                      "severity": "serious",
                      "elementSelector": "p.text",
                      "description": "Insufficient contrast"
                    }
                  ]
                }
                ```""";

        List<Finding> findings = parser.parse(json);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getWcagCriterion()).isEqualTo("1.4.3");
    }
}
