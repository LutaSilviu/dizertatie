package ro.sluta.accessibility.browser;

import java.net.URI;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.browser.interaction.InteractionScenario;

public interface StabilizedBrowserPagePort {
    <T> BrowserPageOperationResult<T> captureAndApply(
            CapturePageSnapshotCommand command,
            URI validatedUrl,
            InteractionScenario scenario,
            StabilizedPageOperation<T> operation);
}
