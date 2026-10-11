package usv.dizertatie.scoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.wcag.WcagCriterionRegistry;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeverityScoringServiceTest {

    private static final int TOTAL_CRITERIA = 87;
    private static final double MAX_WEIGHT = TOTAL_CRITERIA * 4.0;

    private SeverityScoringService service;

    @BeforeEach
    void setUp() throws IOException {
        WcagCriterionRegistry registry = new WcagCriterionRegistry();
        registry.load();
        service = new SeverityScoringService(registry);
    }

    private Finding findingWith(String severity) {
        return Finding.builder()
                .severity(severity)
                .build();
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
    void oneCriticalFinding_scoreLessThan100AndMatchesFormula() {
        double expected = 100.0 - (4.0 / MAX_WEIGHT * 100.0);
        double actual = service.score(List.of(findingWith("critical")));
        assertThat(actual).isLessThan(100.0);
        assertThat(actual).isCloseTo(expected, within(1e-9));
    }

    @Test
    void oneSeriousFinding_scoreLessThan100AndGreaterThanCritical() {
        double criticalScore = service.score(List.of(findingWith("critical")));
        double seriousScore  = service.score(List.of(findingWith("serious")));

        assertThat(seriousScore).isLessThan(100.0);
        assertThat(seriousScore).isGreaterThan(criticalScore);
    }

    @Test
    void oneSeriousFinding_matchesFormula() {
        double expected = 100.0 - (3.0 / MAX_WEIGHT * 100.0);
        assertThat(service.score(List.of(findingWith("serious"))))
                .isCloseTo(expected, within(1e-9));
    }

    @Test
    void mixOfAllSeverities_matchesFormula() {
        // critical(4) + serious(3) + moderate(2) + minor(1) = 10
        List<Finding> findings = List.of(
                findingWith("critical"),
                findingWith("serious"),
                findingWith("moderate"),
                findingWith("minor")
        );
        double expected = 100.0 - (10.0 / MAX_WEIGHT * 100.0);
        double actual = service.score(findings);

        assertThat(actual).isLessThan(100.0);
        assertThat(actual).isCloseTo(expected, within(1e-9));
    }

    @Test
    void manyFindings_scoreNeverBelowZero() {
        // 87 * 4 critical findings would bring raw score to exactly 0;
        // any more should be clamped to 0, not negative
        List<Finding> lotsOfFindings = new java.util.ArrayList<>();
        for (int i = 0; i < TOTAL_CRITERIA * 4 + 10; i++) {
            lotsOfFindings.add(findingWith("critical"));
        }
        assertThat(service.score(lotsOfFindings)).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void caseInsensitiveSeverity_criticalUpperCase_matchesFormula() {
        double expected = 100.0 - (4.0 / MAX_WEIGHT * 100.0);
        assertThat(service.score(List.of(findingWith("CRITICAL"))))
                .isCloseTo(expected, within(1e-9));
    }

    @Test
    void unknownSeverity_treatedAsZeroWeight() {
        // unknown severity contributes 0 weight → same as one moderate minus unknown
        double actual = service.score(List.of(findingWith("unknown_severity")));
        assertThat(actual).isEqualTo(100.0);
    }
}
