package ro.sluta.accessibility.persistence;

import java.time.Instant;
import java.util.UUID;

public record RawResultRecord(UUID rawResultId, UUID runId, String component, UUID artifactId,
        UUID metadataArtifactId, String providerRequestId, String engineVersion, Instant createdAt) {
    public RawResultRecord {
        if (rawResultId == null || runId == null || artifactId == null || createdAt == null) {
            throw new IllegalArgumentException("Identificatorii, artefactul principal și momentul sunt obligatorii.");
        }
        if (component == null || component.isBlank()) {
            throw new IllegalArgumentException("Componenta rezultatului brut este obligatorie.");
        }
    }
}
