package ro.sluta.accessibility.application;

import java.util.UUID;
import ro.sluta.accessibility.domain.Viewport;

public record CapturePageSnapshotCommand(
        UUID runId,
        String url,
        Viewport viewport,
        String interactionScenarioId) {
}
