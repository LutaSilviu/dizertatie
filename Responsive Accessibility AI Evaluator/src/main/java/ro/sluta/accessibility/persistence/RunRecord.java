package ro.sluta.accessibility.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ro.sluta.accessibility.domain.AnalysisMethod;
import ro.sluta.accessibility.domain.RunStatus;

public record RunRecord(UUID runId, UUID analysisId, AnalysisMethod method, String modelId, String condition,
        int repetition, RunStatus status, Instant startedAt, Instant completedAt, Long latencyMs, Long inputTokens,
        Long cachedInputTokens, Long outputTokens, BigDecimal costUsd, Boolean schemaValid, String errorCode,
        String errorMessage, String rawResultRef, long version) { }
