package ro.sluta.accessibility.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ro.sluta.accessibility.browser.BrowserSnapshotPort;
import ro.sluta.accessibility.browser.interaction.InteractionScenario;
import ro.sluta.accessibility.browser.interaction.InteractionScenarioRegistry;
import ro.sluta.accessibility.config.BrowserCaptureProperties;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.SnapshotErrorCode;
import ro.sluta.accessibility.domain.SnapshotStatus;
import ro.sluta.accessibility.security.NetworkAccessPolicy;
import ro.sluta.accessibility.security.NetworkPolicyException;
import ro.sluta.accessibility.security.PublicUrlValidator;
import ro.sluta.accessibility.security.UrlValidationException;

@Service
public class DefaultCapturePageSnapshotService implements CapturePageSnapshotUseCase {
    private final PublicUrlValidator urlValidator;
    private final NetworkAccessPolicy networkPolicy;
    private final InteractionScenarioRegistry scenarios;
    private final BrowserSnapshotPort browser;
    private final BrowserCaptureProperties properties;

    public DefaultCapturePageSnapshotService(PublicUrlValidator urlValidator,
                                             NetworkAccessPolicy networkPolicy,
                                             InteractionScenarioRegistry scenarios,
                                             BrowserSnapshotPort browser,
                                             BrowserCaptureProperties properties) {
        this.urlValidator = urlValidator;
        this.networkPolicy = networkPolicy;
        this.scenarios = scenarios;
        this.browser = browser;
        this.properties = properties;
    }

    @Override
    public PageSnapshot capture(CapturePageSnapshotCommand command) {
        if (command == null || command.runId() == null || command.viewport() == null) {
            return failure(command, SnapshotStatus.FAILED, SnapshotErrorCode.INVALID_URL,
                    "runId și viewport sunt obligatorii.");
        }
        try {
            URI uri = urlValidator.validate(command.url());
            networkPolicy.verify(uri, true);
            InteractionScenario scenario = scenarios.resolve(command.interactionScenarioId(),
                    properties.getLimits().getMaxInteractionSteps());
            return browser.capture(command, uri, scenario);
        } catch (UrlValidationException exception) {
            SnapshotStatus status = exception.errorCode() == SnapshotErrorCode.BLOCKED_ADDRESS
                    ? SnapshotStatus.BLOCKED : SnapshotStatus.FAILED;
            return failure(command, status, exception.errorCode(), exception.getMessage());
        } catch (NetworkPolicyException exception) {
            return failure(command, SnapshotStatus.BLOCKED, exception.errorCode(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return failure(command, SnapshotStatus.FAILED, SnapshotErrorCode.INVALID_INTERACTION_SCENARIO,
                    exception.getMessage());
        }
    }

    private PageSnapshot failure(CapturePageSnapshotCommand command, SnapshotStatus status,
                                 SnapshotErrorCode code, String message) {
        UUID runId = command == null || command.runId() == null ? UUID.randomUUID() : command.runId();
        URI requested = null;
        if (command != null && command.url() != null) {
            try { requested = URI.create(command.url()); } catch (IllegalArgumentException ignored) { }
        }
        return new PageSnapshot(UUID.randomUUID(), runId, requested, null, Instant.now(),
                command == null ? null : command.viewport(), null, null, null, List.of(), List.of(),
                status, List.of(), code, message, properties.getExtractorVersion(), null,
                command == null ? null : command.interactionScenarioId(), List.of());
    }
}
