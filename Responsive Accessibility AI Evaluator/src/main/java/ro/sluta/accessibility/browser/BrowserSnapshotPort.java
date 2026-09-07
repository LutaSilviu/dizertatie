package ro.sluta.accessibility.browser;

import java.net.URI;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.browser.interaction.InteractionScenario;
import ro.sluta.accessibility.domain.PageSnapshot;

public interface BrowserSnapshotPort {
    PageSnapshot capture(CapturePageSnapshotCommand command, URI validatedUrl, InteractionScenario scenario);
}
