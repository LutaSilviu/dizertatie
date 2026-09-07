package ro.sluta.accessibility.domain;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PageSnapshot(
        UUID snapshotId,
        UUID runId,
        URI requestedUrl,
        URI finalUrl,
        Instant capturedAt,
        Viewport viewport,
        BrowserMetadata browser,
        NavigationMetadata navigation,
        SnapshotPageMetadata page,
        List<ArtifactReference> artifacts,
        List<InteractiveElement> interactiveElements,
        SnapshotStatus status,
        List<String> warnings,
        SnapshotErrorCode errorCode,
        String errorMessage,
        String extractorVersion,
        String contentHash,
        String interactionState,
        List<InteractionStepResult> interactionSteps) {

    public PageSnapshot {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        interactiveElements = interactiveElements == null ? List.of() : List.copyOf(interactiveElements);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        interactionSteps = interactionSteps == null ? List.of() : List.copyOf(interactionSteps);
    }
}
