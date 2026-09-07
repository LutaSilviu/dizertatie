package ro.sluta.accessibility.analysis.axe;

import java.net.URI;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.browser.interaction.InteractionScenario;

public interface AxeAnalysisPort {
    AxeAnalysisExecution analyze(
            RunAxeAnalysisCommand command,
            CapturePageSnapshotCommand captureCommand,
            URI validatedUrl,
            InteractionScenario scenario);
}
