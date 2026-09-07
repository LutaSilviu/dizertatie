package ro.sluta.accessibility.analysis.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import ro.sluta.accessibility.domain.ArtifactReference;

public record AiAnalysisResult(
        UUID runId,
        UUID snapshotId,
        String modelId,
        String providerModel,
        AiCondition condition,
        AiRunProfile profile,
        String promptVersion,
        String schemaVersion,
        String contextVersion,
        boolean success,
        JsonNode parsed,
        AiUsage usage,
        AiCost cost,
        AiCost estimatedCost,
        long latencyMs,
        int retryCount,
        String providerResponseId,
        String providerRequestId,
        String reportedModel,
        AiErrorCode errorCode,
        String errorMessage,
        List<ArtifactReference> artifacts) {
    public AiAnalysisResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
