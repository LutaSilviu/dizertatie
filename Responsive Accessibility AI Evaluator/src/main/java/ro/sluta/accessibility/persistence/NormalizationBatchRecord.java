package ro.sluta.accessibility.persistence;

import java.time.Instant;
import java.util.UUID;

public record NormalizationBatchRecord(UUID batchId, UUID analysisId, String normalizerVersion, String matcherVersion,
        String configurationJson, String inputHash, String resultHash, boolean hybrid, Instant createdAt) { }
