package ro.sluta.accessibility.persistence;

import java.time.Instant;
import java.util.UUID;
import ro.sluta.accessibility.domain.AnalysisStatus;

public record AnalysisRecord(UUID analysisId, UUID parentAnalysisId, String requestedUrl, String configurationJson,
        AnalysisStatus status, int progressPercent, Instant createdAt, Instant updatedAt, long version) { }
