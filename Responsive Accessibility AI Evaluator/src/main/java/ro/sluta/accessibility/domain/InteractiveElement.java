package ro.sluta.accessibility.domain;

public record InteractiveElement(
        String elementId,
        String role,
        String accessibleName,
        String tagName,
        String selectorHint,
        boolean visible,
        boolean enabled,
        boolean focusable,
        BoundingBox boundingBox) {
}
