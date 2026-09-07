package ro.sluta.accessibility.analysis.ai;

public enum AiRunProfile {
    APP_DEFAULT_V1(1),
    EXPERIMENT_V1(0);

    private final int maxRetries;

    AiRunProfile(int maxRetries) { this.maxRetries = maxRetries; }
    public int maxRetries() { return maxRetries; }
}
