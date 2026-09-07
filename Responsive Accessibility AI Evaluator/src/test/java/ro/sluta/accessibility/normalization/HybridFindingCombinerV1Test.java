package ro.sluta.accessibility.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.sluta.accessibility.analysis.ai.AiCondition;
import ro.sluta.accessibility.domain.CanonicalSeverity;
import ro.sluta.accessibility.domain.FindingSource;
import ro.sluta.accessibility.domain.Viewport;

class HybridFindingCombinerV1Test {
    private final FindingNormalizerV1 normalizer = new FindingNormalizerV1();
    private final HybridFindingCombinerV1 combiner = new HybridFindingCombinerV1();

    @Test void combinesOnlyMatchingAxeAndMultimodalAiWithoutAnotherCall() throws Exception {
        var axe = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.DESKTOP, List.of("img.hero"), List.of("img.logo")));
        var ai = normalizer.normalizeAi(F5TestFixtures.ai(Viewport.DESKTOP,
                "Missing alt", "img.hero", "Unclear control", "button.save"), Viewport.DESKTOP);
        var result = combiner.combine("S01_BAD_DESKTOP", "GPT5_NANO", 1, AiCondition.AI_MULTIMODAL, axe, ai);
        assertThat(result.additionalAiCalls()).isZero();
        assertThat(result.findings()).extracting(f -> f.source()).containsExactlyInAnyOrder(
                FindingSource.BOTH, FindingSource.AXE_ONLY, FindingSource.AI_ONLY);
        assertThat(result.findings()).filteredOn(f -> f.source() == FindingSource.BOTH).singleElement().satisfies(f -> {
            assertThat(f.sourceRefs()).hasSize(2);
            assertThat(f.matchEvidence().totalScore()).isEqualTo(1);
            assertThat(f.axeSeverity()).isEqualTo(CanonicalSeverity.SERIOUS);
            assertThat(f.aiSeverity()).isEqualTo(CanonicalSeverity.MODERATE);
            assertThat(f.displaySeverity()).isEqualTo(CanonicalSeverity.SERIOUS);
        });
        assertThatThrownBy(() -> combiner.combine("case", "model", 1, AiCondition.AI_TEXT, axe, ai))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void doesNotForceAmbiguousCrossSourceMatches() throws Exception {
        var axe = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.DESKTOP, List.of("img.hero")));
        var ai = normalizer.normalizeAi(F5TestFixtures.ai(Viewport.DESKTOP,
                "Problem one", "img.hero", "Problem two", "img.hero"), Viewport.DESKTOP);
        var result = combiner.combine("case", "model", 1, AiCondition.AI_MULTIMODAL, axe, ai);
        assertThat(result.ambiguousMatches()).hasSize(1);
        assertThat(result.findings()).extracting(f -> f.source()).containsOnly(FindingSource.AXE_ONLY, FindingSource.AI_ONLY);
    }
}
