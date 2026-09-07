package ro.sluta.accessibility.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisExecution;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisPort;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisResult;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisStatus;
import ro.sluta.accessibility.analysis.axe.AxeErrorCode;
import ro.sluta.accessibility.analysis.axe.RunAxeAnalysisCommand;
import ro.sluta.accessibility.analysis.axe.RunAxeAnalysisUseCase;
import ro.sluta.accessibility.browser.interaction.InteractionScenario;
import ro.sluta.accessibility.browser.interaction.InteractionScenarioRegistry;
import ro.sluta.accessibility.config.AxeAnalysisProperties;
import ro.sluta.accessibility.config.BrowserCaptureProperties;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.security.NetworkAccessPolicy;
import ro.sluta.accessibility.security.PublicUrlValidator;

@Service
public class DefaultRunAxeAnalysisService implements RunAxeAnalysisUseCase {
    private final PublicUrlValidator urlValidator;
    private final NetworkAccessPolicy networkPolicy;
    private final InteractionScenarioRegistry scenarios;
    private final BrowserCaptureProperties browserProperties;
    private final AxeAnalysisProperties axeProperties;
    private final AxeAnalysisPort axePort;
    private final CapturePageSnapshotUseCase captureUseCase;

    public DefaultRunAxeAnalysisService(PublicUrlValidator urlValidator,
                                        NetworkAccessPolicy networkPolicy,
                                        InteractionScenarioRegistry scenarios,
                                        BrowserCaptureProperties browserProperties,
                                        AxeAnalysisProperties axeProperties,
                                        AxeAnalysisPort axePort,
                                        CapturePageSnapshotUseCase captureUseCase) {
        this.urlValidator = urlValidator;
        this.networkPolicy = networkPolicy;
        this.scenarios = scenarios;
        this.browserProperties = browserProperties;
        this.axeProperties = axeProperties;
        this.axePort = axePort;
        this.captureUseCase = captureUseCase;
    }

    @Override
    public AxeAnalysisExecution analyze(RunAxeAnalysisCommand command) {
        CapturePageSnapshotCommand captureCommand = new CapturePageSnapshotCommand(
                command.runId(), command.url(), command.viewport(), command.interactionScenarioId());
        URI uri;
        InteractionScenario scenario;
        try {
            uri = urlValidator.validate(command.url());
            networkPolicy.verify(uri, true);
            scenario = scenarios.resolve(command.interactionScenarioId(),
                    browserProperties.getLimits().getMaxInteractionSteps());
        } catch (RuntimeException exception) {
            PageSnapshot snapshot = captureUseCase.capture(captureCommand);
            Instant now = Instant.now();
            AxeAnalysisResult failed = new AxeAnalysisResult(command.analysisId(), command.runId(),
                    snapshot.snapshotId(), snapshot.viewport(), snapshot.finalUrl(), snapshot.contentHash(),
                    axeProperties.getProfileVersion(), axeProperties.getTags(), axeProperties.getAdapterVersion(),
                    null, now, now, 0, axeProperties.getTimeout().toMillis(), AxeAnalysisStatus.FAILED, List.of(),
                    AxeErrorCode.AXE_EXECUTION_FAILED, exception.getMessage(), List.of(), List.of(), List.of(),
                    List.of(), List.of());
            return new AxeAnalysisExecution(snapshot, failed);
        }
        return axePort.analyze(command, captureCommand, uri, scenario);
    }
}
