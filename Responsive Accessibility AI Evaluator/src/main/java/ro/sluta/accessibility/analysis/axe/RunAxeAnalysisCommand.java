package ro.sluta.accessibility.analysis.axe;

import java.util.UUID;
import ro.sluta.accessibility.domain.Viewport;

public record RunAxeAnalysisCommand(
        UUID analysisId,
        UUID runId,
        String url,
        Viewport viewport,
        String interactionScenarioId) {
}
