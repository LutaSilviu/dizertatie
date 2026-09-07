package ro.sluta.accessibility.domain;

public record FindingSourceReference(
        FindingSource source,
        String sourceId,
        String rawReference,
        int itemIndex) {
}
