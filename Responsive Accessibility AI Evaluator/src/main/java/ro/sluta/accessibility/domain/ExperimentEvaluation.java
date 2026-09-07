package ro.sluta.accessibility.domain;

import java.util.Optional;
import java.util.UUID;

public record ExperimentEvaluation(
        UUID evaluationId,
        Optional<UUID> findingId,
        UUID runId,
        String matchedGroundTruthId,
        DetectionClass detectionClass,
        Integer localizationScore,
        Integer wcagScore,
        Integer e1,
        Integer e2,
        Integer e3,
        Integer e4,
        Integer e5,
        Double qualityScore,
        String reviewer,
        String notes) {
    public ExperimentEvaluation {
        findingId = findingId == null ? Optional.empty() : findingId;
    }
}
