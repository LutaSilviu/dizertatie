package ro.sluta.accessibility.reporting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.analysis.ai.AiAnalysisResult;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisResult;
import ro.sluta.accessibility.domain.*;
import ro.sluta.accessibility.normalization.FindingNormalizerV1;
import ro.sluta.accessibility.normalization.HybridFindingCombinerV1;

@Component
public class NormalizedReportAssembler {
    public NormalizedReport assemble(List<PageSnapshot> snapshots, List<PredictedFinding> findings,
            List<ComponentReport> components, Map<String, String> configuration, List<String> limitations,
            long inputTokens, long outputTokens, BigDecimal costUsd) {
        ReportStatus status = status(components);
        List<ViewportComparison> comparisons = new ArrayList<>();
        for (Viewport viewport : Viewport.values()) {
            List<PredictedFinding> values = findings.stream().filter(f -> f.viewport() == viewport).toList();
            comparisons.add(new ViewportComparison(viewport, values.size(), count(values, FindingSource.AXE_ONLY),
                    count(values, FindingSource.AI_ONLY), count(values, FindingSource.BOTH)));
        }
        long duration = components.stream().mapToLong(ComponentReport::durationMs).sum();
        return new NormalizedReport(UUID.randomUUID(), status, configuration, snapshots, components, summary(findings),
                findings, limitations, duration, inputTokens, outputTokens, costUsd == null ? BigDecimal.ZERO : costUsd,
                comparisons, FindingNormalizerV1.VERSION, HybridFindingCombinerV1.VERSION);
    }

    public NormalizedReport forAxe(PageSnapshot snapshot, AxeAnalysisResult result, List<PredictedFinding> findings) {
        ReportStatus componentStatus = switch (result.status()) {
            case SUCCESS -> ReportStatus.SUCCESS; case PARTIAL -> ReportStatus.PARTIAL; case FAILED -> ReportStatus.FAILED;
        };
        List<String> limitations = result.errorMessage() == null ? List.of() : List.of("axe-core: " + result.errorMessage());
        return assemble(List.of(snapshot), findings, List.of(new ComponentReport("BROWSER", ReportStatus.SUCCESS,
                        snapshot.navigation() == null ? 0 : snapshot.navigation().loadDurationMs(), null, null),
                new ComponentReport("AXE", componentStatus, result.durationMs(),
                        result.errorCode() == null ? null : result.errorCode().name(), result.errorMessage())),
                Map.of("method", "AXE", "viewport", result.viewport().name()), limitations, 0, 0, BigDecimal.ZERO);
    }

    public NormalizedReport forAi(PageSnapshot snapshot, AiAnalysisResult result, List<PredictedFinding> findings) {
        ReportStatus componentStatus = result.success() ? ReportStatus.SUCCESS : ReportStatus.FAILED;
        List<String> limitations = new ArrayList<>();
        if (result.parsed() != null) result.parsed().path("limitations").forEach(value -> limitations.add(value.asText()));
        if (result.errorMessage() != null) limitations.add("AI: " + result.errorMessage());
        return assemble(List.of(snapshot), findings, List.of(new ComponentReport("BROWSER", ReportStatus.SUCCESS,
                        snapshot.navigation() == null ? 0 : snapshot.navigation().loadDurationMs(), null, null),
                new ComponentReport("AI", componentStatus, result.latencyMs(),
                        result.errorCode() == null ? null : result.errorCode().name(), result.errorMessage())),
                Map.of("method", result.condition().name(), "model", String.valueOf(result.modelId()),
                        "viewport", snapshot.viewport().name()), limitations, result.usage().inputTokens(),
                result.usage().outputTokens(), result.cost() == null ? BigDecimal.ZERO : result.cost().amountUsd());
    }

    public NormalizedReport filter(NormalizedReport report, ReportFilter filter) {
        if (filter != null && filter.status() != null && report.status() != filter.status())
            return copy(report, List.of());
        List<PredictedFinding> selected = report.findings().stream()
                .filter(f -> filter == null || filter.viewport() == null || f.viewport() == filter.viewport())
                .filter(f -> filter == null || filter.source() == null || f.source() == filter.source())
                .filter(f -> filter == null || filter.severity() == null || f.displaySeverity() == filter.severity())
                .filter(f -> filter == null || filter.wcagCriterion() == null || f.wcagCriteria().contains(filter.wcagCriterion()))
                .filter(f -> filter == null || filter.method() == null || filter.method().isBlank()
                        || report.configuration().getOrDefault("method", "").equalsIgnoreCase(filter.method()))
                .filter(f -> filter == null || filter.model() == null || filter.model().isBlank()
                        || report.configuration().getOrDefault("model", "").equalsIgnoreCase(filter.model()))
                .toList();
        return copy(report, selected);
    }

    private NormalizedReport copy(NormalizedReport report, List<PredictedFinding> findings) {
        return assemble(report.snapshots(), findings, report.components(), report.configuration(), report.limitations(),
                report.inputTokens(), report.outputTokens(), report.costUsd());
    }
    private ReportSummary summary(List<PredictedFinding> findings) {
        Map<CanonicalSeverity, Integer> severities = new EnumMap<>(CanonicalSeverity.class);
        for (CanonicalSeverity value : CanonicalSeverity.values()) severities.put(value, 0);
        findings.forEach(f -> severities.merge(f.displaySeverity(), 1, Integer::sum));
        return new ReportSummary(findings.size(), count(findings, FindingSource.AXE_ONLY), count(findings, FindingSource.AI_ONLY),
                count(findings, FindingSource.BOTH), findings.stream().mapToInt(f -> f.duplicateCount() - 1).sum(),
                severities.get(CanonicalSeverity.CRITICAL), severities.get(CanonicalSeverity.SERIOUS),
                severities.get(CanonicalSeverity.MODERATE), severities.get(CanonicalSeverity.MINOR),
                severities.get(CanonicalSeverity.UNKNOWN));
    }
    private static int count(List<PredictedFinding> findings, FindingSource source) {
        return (int) findings.stream().filter(f -> f.source() == source).count();
    }
    private static ReportStatus status(List<ComponentReport> components) {
        if (components.isEmpty() || components.stream().allMatch(c -> c.status() == ReportStatus.FAILED)) return ReportStatus.FAILED;
        if (components.stream().anyMatch(c -> c.status() != ReportStatus.SUCCESS)) return ReportStatus.PARTIAL;
        return ReportStatus.SUCCESS;
    }
}
