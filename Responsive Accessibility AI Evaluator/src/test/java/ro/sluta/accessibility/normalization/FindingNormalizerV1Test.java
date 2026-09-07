package ro.sluta.accessibility.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.sluta.accessibility.domain.FindingSource;
import ro.sluta.accessibility.domain.Viewport;

class FindingNormalizerV1Test {
    private final FindingNormalizerV1 normalizer = new FindingNormalizerV1();

    @Test void createsOneTraceableFindingPerAxeNodeAndDeduplicatesWithinTheSource() {
        var result = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.DESKTOP,
                List.of("img.hero"), List.of("img.hero"), List.of("img.logo")));
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(finding -> {
            assertThat(finding.source()).isEqualTo(FindingSource.AXE_ONLY);
            assertThat(finding.wcagCriteria()).containsExactly("1.1.1");
            assertThat(finding.rawReference()).endsWith("axe-results.json");
            assertThat(finding.normalizerVersion()).isEqualTo("NORMALIZER_V1");
        });
        assertThat(result).filteredOn(f -> "img.hero".equals(f.location().selector())).singleElement()
                .satisfies(f -> { assertThat(f.duplicateCount()).isEqualTo(2); assertThat(f.sourceRefs()).hasSize(2); });
    }

    @Test void normalizesEveryAiFindingWithoutInventingUnknownElementFields() throws Exception {
        var result = normalizer.normalizeAi(F5TestFixtures.ai(Viewport.DESKTOP,
                "Missing alt", "img.hero", "Missing logo alt", "img.logo"), Viewport.DESKTOP);
        assertThat(result).hasSize(2).allSatisfy(finding -> {
            assertThat(finding.source()).isEqualTo(FindingSource.AI_ONLY);
            assertThat(finding.location().role()).isNull();
            assertThat(finding.sourceRefs()).singleElement().satisfies(ref -> assertThat(ref.rawReference()).endsWith("parsed.json"));
        });
    }

    @Test void sameInputProducesSameKeysButViewportOrSnapshotBoundariesStayDistinct() {
        var first = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.DESKTOP, List.of("img.hero")));
        var second = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.DESKTOP, List.of("img.hero")));
        var mobile = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.MOBILE, List.of("img.hero")));
        assertThat(first.getFirst().findingKey()).isEqualTo(second.getFirst().findingKey()).isNotEqualTo(mobile.getFirst().findingKey());
        assertThat(first.getFirst().findingId()).isEqualTo(second.getFirst().findingId());
    }
}
