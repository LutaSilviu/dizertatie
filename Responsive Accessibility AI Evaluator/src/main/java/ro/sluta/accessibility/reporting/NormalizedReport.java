package ro.sluta.accessibility.reporting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.PredictedFinding;

public record NormalizedReport(
        UUID reportId,
        ReportStatus status,
        Map<String, String> configuration,
        List<PageSnapshot> snapshots,
        List<ComponentReport> components,
        ReportSummary summary,
        List<PredictedFinding> findings,
        List<String> limitations,
        long totalDurationMs,
        long inputTokens,
        long outputTokens,
        BigDecimal costUsd,
        List<ViewportComparison> viewportComparison,
        String normalizerVersion,
        String matcherVersion) {
    public NormalizedReport {
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        components = components == null ? List.of() : List.copyOf(components);
        findings = findings == null ? List.of() : List.copyOf(findings);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        viewportComparison = viewportComparison == null ? List.of() : List.copyOf(viewportComparison);
    }
}
