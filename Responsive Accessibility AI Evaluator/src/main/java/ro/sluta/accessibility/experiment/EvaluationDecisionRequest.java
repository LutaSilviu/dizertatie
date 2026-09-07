package ro.sluta.accessibility.experiment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ro.sluta.accessibility.domain.DetectionClass;

public record EvaluationDecisionRequest(UUID runId, Optional<UUID> findingId, String groundTruthId,
        DetectionClass detectionClass, Integer localizationScore, Integer wcagScore, Integer e1, Integer e2,
        Integer e3, Integer e4, Integer e5, String reviewer, EvaluationDecisionKind decisionKind, boolean blind,
        List<UUID> sourceDecisionIds, String notes) {
    public EvaluationDecisionRequest { findingId = findingId == null ? Optional.empty() : findingId; }
}
