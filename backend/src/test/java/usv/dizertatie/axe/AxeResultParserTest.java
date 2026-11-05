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
    void violationWithNoWcagTagFallsBackToId() {
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
        assertThat(findings.get(0).getWcagCriterion()).isEqualTo("aria-required-attr");
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
    void twoDigitCriterionNumbersAreFormattedCorrectly() {
        assertThat(AxeResultParser.formatWcagCriterion("1410")).isEqualTo("1.4.10");
        assertThat(AxeResultParser.formatWcagCriterion("2411")).isEqualTo("2.4.11");
        assertThat(AxeResultParser.formatWcagCriterion("143")).isEqualTo("1.4.3");
        assertThat(AxeResultParser.formatWcagCriterion("21")).isEqualTo("2.1");
    }
}
