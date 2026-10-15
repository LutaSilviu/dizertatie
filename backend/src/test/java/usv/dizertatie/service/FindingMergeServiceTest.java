package usv.dizertatie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.Finding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingMergeServiceTest {

    private FindingMergeService service;

    @BeforeEach
    void setUp() {
        service = new FindingMergeService();
    }

    private Finding axeFinding(String criterion, String selector) {
        return Finding.builder()
                .sourceMethod("AXE")
                .wcagCriterion(criterion)
                .elementSelector(selector)
                .description("Axe description")
                .severity("critical")
                .build();
    }

    private Finding aiFinding(String criterion, String selector) {
        return Finding.builder()
                .sourceMethod("AI")
                .wcagCriterion(criterion)
                .elementSelector(selector)
                .description("AI description")
                .severity("moderate")
                .build();
    }

    @Test
    void sameCriterionAndSameElement_producesOneFindingWithSourceCombined() {
        Finding axe = axeFinding("1.1.1", "img.logo");
        Finding ai = aiFinding("1.1.1", "img.logo");

        List<Finding> result = service.merge(List.of(axe), List.of(ai));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceMethod()).isEqualTo("COMBINED");
        // Axe data is preferred
        assertThat(result.get(0).getDescription()).isEqualTo("Axe description");
        assertThat(result.get(0).getSeverity()).isEqualTo("critical");
    }

    @Test
    void sameCriterionDifferentElement_producesTwoFindings() {
        Finding axe = axeFinding("1.1.1", "img.logo");
        Finding ai = aiFinding("1.1.1", "img.banner");

        List<Finding> result = service.merge(List.of(axe), List.of(ai));

        assertThat(result).hasSize(2);
    }

    @Test
    void differentCriterion_producesTwoFindings() {
        Finding axe = axeFinding("1.1.1", "img.logo");
        Finding ai = aiFinding("1.4.3", "img.logo");

        List<Finding> result = service.merge(List.of(axe), List.of(ai));

        assertThat(result).hasSize(2);
    }

    @Test
    void emptyAxeList_returnsAiFindingsAsIs() {
        Finding ai = aiFinding("1.1.1", "img.logo");

        List<Finding> result = service.merge(List.of(), List.of(ai));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceMethod()).isEqualTo("AI");
    }

    @Test
    void emptyAiList_returnsAxeFindingsAsIs() {
        Finding axe = axeFinding("1.1.1", "img.logo");

        List<Finding> result = service.merge(List.of(axe), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceMethod()).isEqualTo("AXE");
    }

    @Test
    void bothEmpty_returnsEmptyList() {
        List<Finding> result = service.merge(List.of(), List.of());

        assertThat(result).isEmpty();
    }
}
