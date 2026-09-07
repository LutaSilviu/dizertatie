package ro.sluta.accessibility.analysis.ai;

public record PageContext(
        String version,
        String text,
        int originalCharacters,
        int retainedCharacters,
        boolean truncated,
        String sha256) {
}
