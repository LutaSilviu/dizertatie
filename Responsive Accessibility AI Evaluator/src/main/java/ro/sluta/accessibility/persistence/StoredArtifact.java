package ro.sluta.accessibility.persistence;

import java.time.Instant;
import java.util.UUID;
import ro.sluta.accessibility.domain.ArtifactReference;

public record StoredArtifact(UUID artifactId, UUID runId, UUID snapshotId, ArtifactReference reference, Instant createdAt) { }
