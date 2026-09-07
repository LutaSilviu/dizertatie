package ro.sluta.accessibility.domain;

public record ArtifactReference(
        ArtifactType artifactType,
        String relativePath,
        String mimeType,
        long byteSize,
        String sha256,
        String extractorVersion) {
}
