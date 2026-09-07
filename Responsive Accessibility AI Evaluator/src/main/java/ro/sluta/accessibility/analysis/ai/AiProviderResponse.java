package ro.sluta.accessibility.analysis.ai;

public record AiProviderResponse(
        String rawResponse,
        String outputText,
        AiUsage usage,
        String responseId,
        String requestId,
        String reportedModel,
        long latencyMs,
        boolean refusal,
        boolean truncated) {
}
