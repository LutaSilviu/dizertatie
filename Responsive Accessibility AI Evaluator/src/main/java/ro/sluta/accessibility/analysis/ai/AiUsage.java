package ro.sluta.accessibility.analysis.ai;

public record AiUsage(long inputTokens, long cachedInputTokens, long outputTokens, long totalTokens) {
    public static AiUsage empty() { return new AiUsage(0, 0, 0, 0); }
}
