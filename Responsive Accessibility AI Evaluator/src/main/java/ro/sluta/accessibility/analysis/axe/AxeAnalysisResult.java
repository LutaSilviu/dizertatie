package ro.sluta.accessibility.analysis.axe;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.Viewport;

public record AxeAnalysisResult(
        UUID analysisId,
        UUID runId,
        UUID snapshotId,
        Viewport viewport,
        URI finalUrl,
        String contentHash,
        String profileVersion,
        List<String> tags,
        String adapterVersion,
        String engineVersion,
        Instant startedAt,
        Instant completedAt,
        long durationMs,
        long timeoutMs,
        AxeAnalysisStatus status,
        List<String> warnings,
        AxeErrorCode errorCode,
        String errorMessage,
        List<AxeRuleResult> violations,
        List<AxeRuleResult> incomplete,
        List<AxeRuleResult> passes,
        List<AxeRuleResult> inapplicable,
        List<ArtifactReference> artifacts) {
    public AxeAnalysisResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        violations = violations == null ? List.of() : List.copyOf(violations);
        incomplete = incomplete == null ? List.of() : List.copyOf(incomplete);
        passes = passes == null ? List.of() : List.copyOf(passes);
        inapplicable = inapplicable == null ? List.of() : List.copyOf(inapplicable);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public int violationNodeCount() { return violations.stream().mapToInt(rule -> rule.nodes().size()).sum(); }
    public int incompleteNodeCount() { return incomplete.stream().mapToInt(rule -> rule.nodes().size()).sum(); }
    public int totalRuleCount() { return violations.size() + incomplete.size() + passes.size() + inapplicable.size(); }
}
