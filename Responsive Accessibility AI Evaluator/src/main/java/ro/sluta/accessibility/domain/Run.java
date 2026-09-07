package ro.sluta.accessibility.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Run(
        UUID runId,
        UUID analysisId,
        RunStatus status,
        AnalysisMethod method,
        String modelId,
        int repetition,
        Instant startedAt,
        Instant completedAt,
        Long inputTokens,
        Long outputTokens,
        Long latencyMs,
        String costUsd,
        Boolean schemaValid,
        List<String> errors,
        String rawResultRef) {
}
