package usv.dizertatie.scoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.Finding;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeverityScoringServiceTest {

    private SeverityScoringService service;

    @BeforeEach
    void setUp() {
        service = new SeverityScoringService();
    }

    private static Finding finding(String criterion, String severity) {
        return Finding.builder().wcagCriterion(criterion).severity(severity).build();
    }

    private static List<Finding> repeat(int n, String criterion, String severity) {
        List<Finding> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(finding(criterion, severity));
        }
        return list;
    }

    private static double expected(double penalty) {
        return Math.round(100.0 * Math.exp(-penalty / SeverityScoringService.DECAY) * 10.0) / 10.0;
    }

    @Test
    void noFindings_returns100() {
        assertThat(service.score(List.of())).isEqualTo(100.0);
    }

    @Test
    void nullFindings_returns100() {
        assertThat(service.score(null)).isEqualTo(100.0);
    }

    @Test
    void oneCriticalFinding_matchesFormula() {
        double actual = service.score(List.of(finding("1.1.1", "critical")));
        assertThat(actual).isEqualTo(expected(4.0));
        assertThat(actual).isCloseTo(85.2, within(0.05));
    }

    @Test
    void oneSeriousFinding_scoresAboveCriticalAndBelow100() {
        double critical = service.score(List.of(finding("1.1.1", "critical")));
        double serious = service.score(List.of(finding("1.1.1", "serious")));
        assertThat(serious).isLessThan(100.0).isGreaterThan(critical);
        assertThat(serious).isEqualTo(expected(3.0));
    }

    @Test
    void oneMinorFinding_isStillVisiblyBelow100() {
        assertThat(service.score(List.of(finding("2.4.4", "minor")))).isEqualTo(expected(1.0)).isLessThan(97.0);
    }

    @Test
    void severitiesOnDifferentCriteria_addUp() {
        List<Finding> findings = List.of(
                finding("1.1.1", "critical"),
                finding("1.4.3", "serious"),
                finding("2.4.4", "moderate"),
                finding("3.1.1", "minor"));
        assertThat(service.score(findings)).isEqualTo(expected(10.0));
    }

    @Test
    void repeatedInstancesOfOneCriterion_growLogarithmically_andUseWorstSeverity() {
        List<Finding> findings = new ArrayList<>(repeat(9, "1.4.3", "moderate"));
        findings.add(finding("1.4.3", "serious"));
        // worst severity serious (3) × (1 + log10(10)) = 6
        assertThat(service.score(findings)).isEqualTo(expected(6.0));
    }

    @Test
    void manySeriousViolations_lowerTheScoreSubstantially() {
        // Thirty colour-contrast failures: a real, visible problem for users.
        assertThat(service.score(repeat(30, "1.4.3", "serious"))).isBetween(70.0, 78.0);

        List<Finding> fiveCriteria = new ArrayList<>();
        for (String sc : List.of("1.1.1", "1.3.1", "1.4.3", "2.4.4", "4.1.2")) {
            fiveCriteria.addAll(repeat(5, sc, "serious"));
        }
        assertThat(service.score(fiveCriteria)).isBetween(30.0, 42.0);
    }

    @Test
    void sameIssueReportedPerNodeOrOnce_scoresSimilarly() {
        // axe reports one finding per node; the AI typically reports the issue once.
        double perNode = service.score(repeat(3, "1.1.1", "critical"));
        double once = service.score(List.of(finding("1.1.1", "critical")));
        assertThat(once - perNode).isLessThan(10.0);
    }

    @Test
    void scoreStaysWithinRange_forHugeNumbersOfFindings() {
        List<Finding> lots = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lots.add(finding("x." + i, "critical"));
        }
        assertThat(service.score(lots)).isBetween(0.0, 100.0);
    }

    @Test
    void findingsWithoutCriterion_areGroupedByDescription() {
        Finding a = Finding.builder().severity("serious").description("Missing label").build();
        Finding b = Finding.builder().severity("serious").description("missing label ").build();
        Finding c = Finding.builder().severity("serious").description("Other").build();
        // group "missing label": 3 × (1 + log10 2); group "other": 3
        assertThat(service.score(List.of(a, b, c))).isEqualTo(expected(3.0 * (1 + Math.log10(2)) + 3.0));
    }

    @Test
    void caseInsensitiveSeverity() {
        assertThat(service.score(List.of(finding("1.1.1", "CRITICAL")))).isEqualTo(expected(4.0));
    }

    @Test
    void unknownSeverity_treatedAsZeroWeight() {
        assertThat(service.score(List.of(finding("1.1.1", "unknown_severity")))).isEqualTo(100.0);
    }
}
