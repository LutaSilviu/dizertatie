package usv.dizertatie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import usv.dizertatie.domain.*;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private BenchmarkPageRepository benchmarkPageRepository;

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private GroundTruthLabelRepository groundTruthLabelRepository;

    private MetricsService service;

    // ---- common fixtures ---------------------------------------------------

    private static final String URL = "https://example.com";

    private final BenchmarkRun run = BenchmarkRun.builder()
            .id(1L).name("test-run").status("COMPLETE")
            .createdAt(LocalDateTime.now()).build();

    /** One page in the run; each scan id points at the same scan for simplicity. */
    private final BenchmarkPage page = BenchmarkPage.builder()
            .id(10L).run(run).url(URL).status("DONE")
            .axeScanId(1L).aiScanId(2L).combinedScanId(3L)
            .build();

    @BeforeEach
    void setUp() {
        service = new MetricsService(benchmarkPageRepository, findingRepository, groundTruthLabelRepository);
    }

    // ---- helpers -----------------------------------------------------------

    private static Scan scanWith(long id, String method) {
        return Scan.builder().id(id).url(URL).method(method)
                .timestamp(LocalDateTime.now()).build();
    }

    private static Finding finding(long scanId, String criterion, String selector) {
        Scan s = Scan.builder().id(scanId).url(URL).method("AXE")
                .timestamp(LocalDateTime.now()).build();
        return Finding.builder().id(scanId * 100).scan(s)
                .wcagCriterion(criterion).elementSelector(selector)
                .description("desc").build();
    }

    private static GroundTruthLabel label(String criterion, String selector) {
        return GroundTruthLabel.builder()
                .id((long) (criterion.hashCode() ^ selector.hashCode()))
                .url(URL).wcagCriterion(criterion).elementSelector(selector)
                .build();
    }

    // ---- tests -------------------------------------------------------------

    /**
     * Perfect match: each method finds exactly what the ground-truth says.
     * Expected: P=1.0, R=1.0, F1=1.0 for every method.
     */
    @Test
    void compute_perfectMatch_allMetricsOne() {
        GroundTruthLabel gt = label("1.1.1", "#img");
        Finding f = finding(1L, "1.1.1", "#img");

        when(benchmarkPageRepository.findByRunId(run.getId())).thenReturn(List.of(page));
        when(groundTruthLabelRepository.findByUrl(URL)).thenReturn(List.of(gt));
        when(findingRepository.findByScanId(anyLong())).thenReturn(List.of(f));

        BenchmarkMetrics metrics = service.compute(run);

        for (String method : MetricsService.METHODS) {
            MethodMetrics m = metrics.methods().get(method);
            assertThat(m.precision()).as(method + " precision").isEqualTo(1.0, offset(1e-9));
            assertThat(m.recall()).as(method + " recall").isEqualTo(1.0, offset(1e-9));
            assertThat(m.f1()).as(method + " f1").isEqualTo(1.0, offset(1e-9));
        }
        assertThat(metrics.falsePositiveExamples()).isEmpty();
        assertThat(metrics.falseNegativeExamples()).isEmpty();
    }

    /**
     * All false positives: findings exist but there are no ground-truth labels.
     * Expected: P=0.0, R=0.0 (no FN because there are no labels).
     */
    @Test
    void compute_allFalsePositives_precisionZeroRecallZero() {
        Finding f = finding(1L, "2.1.1", "#btn");

        when(benchmarkPageRepository.findByRunId(run.getId())).thenReturn(List.of(page));
        when(groundTruthLabelRepository.findByUrl(URL)).thenReturn(List.of()); // no ground truth
        when(findingRepository.findByScanId(anyLong())).thenReturn(List.of(f));

        BenchmarkMetrics metrics = service.compute(run);

        for (String method : MetricsService.METHODS) {
            MethodMetrics m = metrics.methods().get(method);
            assertThat(m.precision()).as(method + " precision").isEqualTo(0.0, offset(1e-9));
            assertThat(m.recall()).as(method + " recall").isEqualTo(0.0, offset(1e-9));
            assertThat(m.f1()).as(method + " f1").isEqualTo(0.0, offset(1e-9));
        }
        assertThat(metrics.falsePositiveExamples()).isNotEmpty();
        assertThat(metrics.falseNegativeExamples()).isEmpty();
    }

    /**
     * All false negatives: ground-truth labels exist but no findings are produced.
     * Expected: P=0.0, R=0.0, F1=0.0 for every method.
     */
    @Test
    void compute_allFalseNegatives_allMetricsZero() {
        GroundTruthLabel gt = label("3.1.1", "#lang");

        when(benchmarkPageRepository.findByRunId(run.getId())).thenReturn(List.of(page));
        when(groundTruthLabelRepository.findByUrl(URL)).thenReturn(List.of(gt));
        when(findingRepository.findByScanId(anyLong())).thenReturn(List.of()); // no findings

        BenchmarkMetrics metrics = service.compute(run);

        for (String method : MetricsService.METHODS) {
            MethodMetrics m = metrics.methods().get(method);
            assertThat(m.precision()).as(method + " precision").isEqualTo(0.0, offset(1e-9));
            assertThat(m.recall()).as(method + " recall").isEqualTo(0.0, offset(1e-9));
            assertThat(m.f1()).as(method + " f1").isEqualTo(0.0, offset(1e-9));
        }
        assertThat(metrics.falsePositiveExamples()).isEmpty();
        assertThat(metrics.falseNegativeExamples()).isNotEmpty();
    }

    /**
     * Partial match: 1 TP, 1 FP, 1 FN per method.
     * Expected: P = 1/2 = 0.5, R = 1/2 = 0.5, F1 = 0.5.
     */
    @Test
    void compute_partialMatch_correctPrecisionRecallF1() {
        // Ground truth: two labels
        GroundTruthLabel gt1 = label("1.4.3", "#text");   // will be matched (TP)
        GroundTruthLabel gt2 = label("1.4.4", "#resize"); // not found   (FN)

        // Findings: first matches gt1 (TP), second doesn't match anything (FP)
        Finding f1 = finding(1L, "1.4.3", "#text");    // TP
        Finding f2 = finding(2L, "2.4.1", "#skip");    // FP

        when(benchmarkPageRepository.findByRunId(run.getId())).thenReturn(List.of(page));
        when(groundTruthLabelRepository.findByUrl(URL)).thenReturn(List.of(gt1, gt2));
        when(findingRepository.findByScanId(anyLong())).thenReturn(List.of(f1, f2));

        BenchmarkMetrics metrics = service.compute(run);

        for (String method : MetricsService.METHODS) {
            MethodMetrics m = metrics.methods().get(method);
            // TP=1, FP=1 → precision=0.5
            assertThat(m.precision()).as(method + " precision").isEqualTo(0.5, offset(1e-9));
            // TP=1, FN=1 → recall=0.5
            assertThat(m.recall()).as(method + " recall").isEqualTo(0.5, offset(1e-9));
            // F1 = 2*0.5*0.5/(0.5+0.5) = 0.5
            assertThat(m.f1()).as(method + " f1").isEqualTo(0.5, offset(1e-9));
        }

        assertThat(metrics.falsePositiveExamples()).isNotEmpty();
        assertThat(metrics.falseNegativeExamples()).isNotEmpty();
    }

    /**
     * Verifies that WCAG category breakdown maps criteria to the correct principle names.
     */
    @Test
    void compute_byCategory_groupsCorrectly() {
        GroundTruthLabel gt1 = label("1.1.1", "#img");
        GroundTruthLabel gt2 = label("2.1.1", "#btn");
        Finding f1 = finding(1L, "1.1.1", "#img");
        Finding f2 = finding(2L, "2.1.1", "#btn");

        when(benchmarkPageRepository.findByRunId(run.getId())).thenReturn(List.of(page));
        when(groundTruthLabelRepository.findByUrl(URL)).thenReturn(List.of(gt1, gt2));
        when(findingRepository.findByScanId(anyLong())).thenReturn(List.of(f1, f2));

        BenchmarkMetrics metrics = service.compute(run);

        assertThat(metrics.byCategory()).containsKey("Perceivable");
        assertThat(metrics.byCategory()).containsKey("Operable");
        assertThat(metrics.byCategory().get("Perceivable").get(MetricsService.AXE).f1())
                .isEqualTo(1.0, offset(1e-9));
        assertThat(metrics.byCategory().get("Operable").get(MetricsService.AXE).f1())
                .isEqualTo(1.0, offset(1e-9));
    }

    /**
     * No pages in the run → all metrics should be zero with no exceptions.
     */
    @Test
    void compute_noPages_returnsZeroMetrics() {
        when(benchmarkPageRepository.findByRunId(run.getId())).thenReturn(List.of());

        BenchmarkMetrics metrics = service.compute(run);

        for (String method : MetricsService.METHODS) {
            MethodMetrics m = metrics.methods().get(method);
            assertThat(m.precision()).isEqualTo(0.0, offset(1e-9));
            assertThat(m.recall()).isEqualTo(0.0, offset(1e-9));
            assertThat(m.f1()).isEqualTo(0.0, offset(1e-9));
        }
        assertThat(metrics.byCategory()).isEmpty();
    }
}
