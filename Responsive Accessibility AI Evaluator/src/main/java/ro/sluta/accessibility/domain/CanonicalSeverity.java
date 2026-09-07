package ro.sluta.accessibility.domain;

public enum CanonicalSeverity {
    UNKNOWN(0), MINOR(1), MODERATE(2), SERIOUS(3), CRITICAL(4);
    private final int rank;
    CanonicalSeverity(int rank) { this.rank = rank; }
    public int rank() { return rank; }
    public static CanonicalSeverity maximum(CanonicalSeverity first, CanonicalSeverity second) {
        CanonicalSeverity a = first == null ? UNKNOWN : first;
        CanonicalSeverity b = second == null ? UNKNOWN : second;
        return a.rank >= b.rank ? a : b;
    }
}
