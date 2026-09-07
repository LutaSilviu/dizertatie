package ro.sluta.accessibility.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ro.sluta.accessibility.domain.CanonicalSeverity;
import ro.sluta.accessibility.domain.FindingSource;
import ro.sluta.accessibility.domain.Viewport;
import ro.sluta.accessibility.normalization.F5TestFixtures;
import ro.sluta.accessibility.normalization.FindingNormalizerV1;

class NormalizedReportAssemblerTest {
    private final FindingNormalizerV1 normalizer = new FindingNormalizerV1();
    private final NormalizedReportAssembler assembler = new NormalizedReportAssembler();

    @Test void reportsPartialComponentsAndKeepsViewportComparisonSeparate() {
        var desktop = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.DESKTOP, List.of("img.hero")));
        var mobile = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.MOBILE, List.of("img.hero")));
        var report = assembler.assemble(List.of(F5TestFixtures.snapshot(Viewport.DESKTOP), F5TestFixtures.snapshot(Viewport.MOBILE)),
                java.util.stream.Stream.concat(desktop.stream(), mobile.stream()).toList(),
                List.of(new ComponentReport("BROWSER", ReportStatus.SUCCESS, 5, null, null),
                        new ComponentReport("AI", ReportStatus.FAILED, 90, "AI_TIMEOUT", "timeout")),
                Map.of("method", "COMPARE", "model", "GPT5_NANO"), List.of("AI indisponibil"),
                100, 20, new BigDecimal("0.001"));
        assertThat(report.status()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.findings()).hasSize(2);
        assertThat(report.viewportComparison()).filteredOn(v -> v.viewport() == Viewport.DESKTOP).singleElement()
                .satisfies(v -> assertThat(v.total()).isEqualTo(1));
        assertThat(report.viewportComparison()).filteredOn(v -> v.viewport() == Viewport.MOBILE).singleElement()
                .satisfies(v -> assertThat(v.total()).isEqualTo(1));
        assertThat(report.limitations()).contains("AI indisponibil");
    }

    @Test void filtersByViewportMethodModelSourceWcagSeverityAndState() {
        var findings = normalizer.normalizeAxe(F5TestFixtures.axe(Viewport.DESKTOP, List.of("img.hero")));
        var report = assembler.assemble(List.of(F5TestFixtures.snapshot(Viewport.DESKTOP)), findings,
                List.of(new ComponentReport("AXE", ReportStatus.SUCCESS, 10, null, null)),
                Map.of("method", "AXE", "model", "NONE"), List.of(), 0, 0, BigDecimal.ZERO);
        var selected = assembler.filter(report, new ReportFilter(Viewport.DESKTOP, "AXE", "NONE",
                FindingSource.AXE_ONLY, "1.1.1", CanonicalSeverity.SERIOUS, ReportStatus.SUCCESS));
        assertThat(selected.findings()).hasSize(1);
        assertThat(assembler.filter(report, new ReportFilter(Viewport.MOBILE, null, null,
                null, null, null, null)).findings()).isEmpty();
        assertThat(assembler.filter(report, new ReportFilter(null, null, null,
                null, null, null, ReportStatus.PARTIAL)).findings()).isEmpty();
    }
}
