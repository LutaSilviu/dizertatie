package ro.sluta.accessibility.domain;

import java.util.List;
import java.util.UUID;

public record PredictedFinding(
        UUID findingId,
        UUID runId,
        UUID snapshotId,
        Viewport viewport,
        FindingSource source,
        List<FindingSourceReference> sourceRefs,
        String title,
        String description,
        String primaryWcagCriterion,
        List<String> wcagCriteria,
        CanonicalSeverity severity,
        CanonicalSeverity axeSeverity,
        CanonicalSeverity aiSeverity,
        CanonicalSeverity displaySeverity,
        CanonicalConformanceLevel conformanceLevel,
        String normalizedCategory,
        String elementFingerprint,
        FindingLocation location,
        List<String> explanations,
        String impact,
        String recommendation,
        Double confidence,
        String rawReference,
        String findingKey,
        int duplicateCount,
        LocationMatchEvidence matchEvidence,
        String normalizerVersion) {
    public PredictedFinding {
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        wcagCriteria = wcagCriteria == null ? List.of() : List.copyOf(wcagCriteria);
        explanations = explanations == null ? List.of() : List.copyOf(explanations);
        duplicateCount = Math.max(1, duplicateCount);
    }
}
