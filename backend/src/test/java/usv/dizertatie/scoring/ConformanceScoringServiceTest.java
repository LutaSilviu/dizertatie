package usv.dizertatie.scoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.wcag.WcagCriterionRegistry;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConformanceScoringServiceTest {

    private ConformanceScoringService service;

    @BeforeEach
    void setUp() throws Exception {
        WcagCriterionRegistry registry = new WcagCriterionRegistry();
        Method load = WcagCriterionRegistry.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(registry);
        service = new ConformanceScoringService(registry);
    }

    @Test
    void noFindingsReturnsAllHundred() {
        ConformanceScores scores = service.score(List.of());

        assertThat(scores.levelA()).isEqualTo(100.0);
        assertThat(scores.levelAA()).isEqualTo(100.0);
        assertThat(scores.levelAAA()).isEqualTo(100.0);
    }

    @Test
    void oneLevelAFindingReducesOnlyLevelA() {
        Finding finding = Finding.builder()
                .wcagCriterion("1.1.1")
                .build();

        ConformanceScores scores = service.score(List.of(finding));

        assertThat(scores.levelA()).isLessThan(100.0);
        assertThat(scores.levelAA()).isEqualTo(100.0);
        assertThat(scores.levelAAA()).isEqualTo(100.0);
    }

    @Test
    void oneLevelAAFindingReducesOnlyLevelAA() {
        Finding finding = Finding.builder()
                .wcagCriterion("1.4.3")
                .build();

        ConformanceScores scores = service.score(List.of(finding));

        assertThat(scores.levelA()).isEqualTo(100.0);
        assertThat(scores.levelAA()).isLessThan(100.0);
        assertThat(scores.levelAAA()).isEqualTo(100.0);
    }

    @Test
    void oneLevelAAAFindingReducesOnlyLevelAAA() {
        Finding finding = Finding.builder()
                .wcagCriterion("1.4.6")
                .build();

        ConformanceScores scores = service.score(List.of(finding));

        assertThat(scores.levelA()).isEqualTo(100.0);
        assertThat(scores.levelAA()).isEqualTo(100.0);
        assertThat(scores.levelAAA()).isLessThan(100.0);
    }

    @Test
    void mixedFindingsAcrossAllLevelsReducesAllScores() {
        List<Finding> findings = List.of(
                Finding.builder().wcagCriterion("1.1.1").build(),   // Level A
                Finding.builder().wcagCriterion("1.4.3").build(),   // Level AA
                Finding.builder().wcagCriterion("1.4.6").build()    // Level AAA
        );

        ConformanceScores scores = service.score(findings);

        assertThat(scores.levelA()).isLessThan(100.0);
        assertThat(scores.levelAA()).isLessThan(100.0);
        assertThat(scores.levelAAA()).isLessThan(100.0);
    }
}
