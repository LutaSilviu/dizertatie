package ro.sluta.accessibility.domain;

public record SnapshotPageMetadata(
        String title,
        String language,
        String contentType,
        double documentWidth,
        int viewportWidth,
        boolean horizontalOverflow) {
}
