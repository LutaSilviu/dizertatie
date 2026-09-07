package ro.sluta.accessibility.domain;

public record FindingLocation(
        String selector,
        String htmlSnippet,
        String role,
        String accessibleName,
        String visibleText,
        String domPath) {
}
