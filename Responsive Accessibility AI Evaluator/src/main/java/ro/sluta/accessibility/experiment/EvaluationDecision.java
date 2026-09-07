package ro.sluta.accessibility.experiment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ro.sluta.accessibility.domain.DetectionClass;

public record EvaluationDecision(UUID decisionId, UUID runId, Optional<UUID> findingId, String groundTruthId,
        DetectionClass detectionClass, Integer localizationScore, Integer wcagScore, Integer e1, Integer e2,
        Integer e3, Integer e4, Integer e5, Double qualityScore, String reviewer, EvaluationDecisionKind decisionKind,
        boolean blind, List<UUID> sourceDecisionIds, String notes, Instant createdAt) {
    public EvaluationDecision {
        findingId = findingId == null ? Optional.empty() : findingId;
        sourceDecisionIds = sourceDecisionIds == null ? List.of() : List.copyOf(sourceDecisionIds);
        if (decisionId == null || runId == null || detectionClass == null || reviewer == null || reviewer.isBlank()
                || decisionKind == null || createdAt == null) throw new IllegalArgumentException("Decizia de evaluare este incompletă.");
        for (Integer score : List.of(localizationScore, wcagScore, e1, e2, e3, e4, e5).stream().filter(java.util.Objects::nonNull).toList())
            if (score < 0 || score > 2) throw new IllegalArgumentException("Scorurile trebuie să fie între 0 și 2.");
        if (decisionKind == EvaluationDecisionKind.ADJUDICATED && sourceDecisionIds.size() < 2)
            throw new IllegalArgumentException("Adjudecarea trebuie să refere cel puțin două decizii individuale.");
    }
}
