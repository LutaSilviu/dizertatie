package ro.sluta.accessibility.domain;

import java.time.Instant;
import java.util.UUID;

public record Analysis(
        UUID analysisId,
        AnalysisRequest request,
        AnalysisStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
