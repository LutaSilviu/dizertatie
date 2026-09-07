package ro.sluta.accessibility.domain;

public record InteractionStepResult(
        int index,
        String action,
        String target,
        boolean success,
        long durationMs,
        String message) {
}
