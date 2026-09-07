package ro.sluta.accessibility.analysis.axe;

import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Check;
import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Node;
import com.deque.html.axecore.results.Rule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.browser.ArtifactWriteException;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.browser.BrowserPageOperationResult;
import ro.sluta.accessibility.browser.StabilizedBrowserPagePort;
import ro.sluta.accessibility.browser.interaction.InteractionScenario;
import ro.sluta.accessibility.config.AxeAnalysisProperties;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.ArtifactType;
import ro.sluta.accessibility.domain.PageSnapshot;

@Component
public class PlaywrightAxeAnalysisAdapter implements AxeAnalysisPort {
    private final StabilizedBrowserPagePort browser;
    private final AxeEngine engine;
    private final AxeAnalysisProperties properties;
    private final AtomicArtifactStore artifactStore;
    private final ObjectMapper objectMapper;

    public PlaywrightAxeAnalysisAdapter(StabilizedBrowserPagePort browser,
                                        AxeEngine engine,
                                        AxeAnalysisProperties properties,
                                        AtomicArtifactStore artifactStore,
                                        ObjectMapper objectMapper) {
        this.browser = browser;
        this.engine = engine;
        this.properties = properties;
        this.artifactStore = artifactStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public AxeAnalysisExecution analyze(RunAxeAnalysisCommand command,
                                        CapturePageSnapshotCommand captureCommand,
                                        URI validatedUrl,
                                        InteractionScenario scenario) {
        BrowserPageOperationResult<AxeAnalysisResult> captured = browser.captureAndApply(
                captureCommand, validatedUrl, scenario,
                (page, snapshot) -> analyzeStabilizedPage(command, page, snapshot));
        AxeAnalysisResult result = captured.operationResult();
        if (result == null) {
            result = failedWithoutPage(command, captured.snapshot(),
                    "axe-core nu a rulat deoarece snapshotul utilizabil nu a fost creat.");
        }
        return new AxeAnalysisExecution(captured.snapshot(), result);
    }

    AxeAnalysisResult analyzeStabilizedPage(RunAxeAnalysisCommand command, Page page, PageSnapshot snapshot) {
        Instant startedAt = Instant.now();
        List<String> warnings = new ArrayList<>();
        String actualAdapterVersion = engine.adapterVersion();
        if (!properties.getAdapterVersion().equals(actualAdapterVersion)) {
            return failure(command, snapshot, startedAt, AxeErrorCode.AXE_VERSION_MISMATCH,
                    "Versiunea adaptorului axe-core rezolvată este " + actualAdapterVersion
                            + ", dar configurația cere " + properties.getAdapterVersion() + '.');
        }

        try {
            AxeResults raw = engine.analyze(page, properties.getTags());
            Instant completedAt = Instant.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();
            if (durationMs > properties.getTimeout().toMillis()) {
                return failure(command, snapshot, startedAt, AxeErrorCode.AXE_EXECUTION_TIMEOUT,
                        "Execuția axe-core a depășit timeoutul configurat de "
                                + properties.getTimeout().toMillis() + " ms.");
            }
            if (raw == null || raw.isErrored()) {
                String detail = raw == null ? "Rezultatul axe-core este null."
                        : "axe-core a raportat o eroare: " + raw.getErrorMessage();
                return failure(command, snapshot, startedAt, AxeErrorCode.AXE_INVALID_RESULT, detail);
            }

            String engineVersion = raw.getTestEngine() == null ? null : raw.getTestEngine().getVersion();
            if (engineVersion == null || engineVersion.isBlank()) {
                return failure(command, snapshot, startedAt, AxeErrorCode.AXE_INVALID_RESULT,
                        "Rezultatul axe-core nu conține versiunea motorului.");
            }
            List<AxeRuleResult> violations = mapRules(raw.getViolations());
            List<AxeRuleResult> incomplete = mapRules(raw.getIncomplete());
            List<AxeRuleResult> passes = mapRules(raw.getPasses());
            List<AxeRuleResult> inapplicable = mapRules(raw.getInapplicable());

            byte[] rawBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(raw);
            ArtifactReference rawArtifact = artifactStore.writeRunArtifact(command.runId(), "axe",
                    "axe-results.json", ArtifactType.AXE_RESULTS_JSON, "application/json", rawBytes,
                    properties.getAdapterVersion());
            List<ArtifactReference> artifacts = new ArrayList<>();
            artifacts.add(rawArtifact);
            AxeAnalysisStatus status = AxeAnalysisStatus.SUCCESS;
            AxeErrorCode errorCode = null;
            String errorMessage = null;

            Map<String, Object> metadata = metadata(command, snapshot, startedAt, completedAt, durationMs,
                    engineVersion, status, warnings, rawArtifact);
            try {
                byte[] metadataBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata);
                artifacts.add(artifactStore.writeRunArtifact(command.runId(), "axe", "axe-metadata.json",
                        ArtifactType.AXE_METADATA_JSON, "application/json", metadataBytes,
                        properties.getAdapterVersion()));
            } catch (JsonProcessingException | ArtifactWriteException exception) {
                status = AxeAnalysisStatus.PARTIAL;
                errorCode = AxeErrorCode.AXE_RESULT_WRITE_FAILED;
                errorMessage = concise(exception.getMessage());
                warnings.add("Rezultatul brut există, dar metadata axe-core nu a putut fi publicată.");
            }

            return result(command, snapshot, startedAt, completedAt, durationMs, engineVersion, status,
                    warnings, errorCode, errorMessage, violations, incomplete, passes, inapplicable, artifacts);
        } catch (ArtifactWriteException exception) {
            return failure(command, snapshot, startedAt, AxeErrorCode.AXE_RESULT_WRITE_FAILED,
                    concise(exception.getMessage()));
        } catch (TimeoutError exception) {
            return failure(command, snapshot, startedAt, AxeErrorCode.AXE_EXECUTION_TIMEOUT,
                    concise(exception.getMessage()));
        } catch (JsonProcessingException exception) {
            return failure(command, snapshot, startedAt, AxeErrorCode.AXE_INVALID_RESULT,
                    "Rezultatul axe-core nu poate fi serializat integral: " + concise(exception.getMessage()));
        } catch (PlaywrightException exception) {
            AxeErrorCode code = looksLikeInjectionFailure(exception)
                    ? AxeErrorCode.AXE_INJECTION_FAILED : AxeErrorCode.AXE_EXECUTION_FAILED;
            return failure(command, snapshot, startedAt, code, concise(exception.getMessage()));
        } catch (RuntimeException exception) {
            return failure(command, snapshot, startedAt, AxeErrorCode.AXE_EXECUTION_FAILED,
                    concise(exception.getMessage()));
        }
    }

    private AxeAnalysisResult failedWithoutPage(RunAxeAnalysisCommand command, PageSnapshot snapshot,
                                                String message) {
        Instant now = Instant.now();
        return result(command, snapshot, now, now, 0, null, AxeAnalysisStatus.FAILED, List.of(),
                AxeErrorCode.AXE_EXECUTION_FAILED, message, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private AxeAnalysisResult failure(RunAxeAnalysisCommand command, PageSnapshot snapshot, Instant startedAt,
                                      AxeErrorCode code, String message) {
        Instant completedAt = Instant.now();
        return result(command, snapshot, startedAt, completedAt,
                Duration.between(startedAt, completedAt).toMillis(), null, AxeAnalysisStatus.FAILED,
                List.of(), code, message, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private AxeAnalysisResult result(RunAxeAnalysisCommand command, PageSnapshot snapshot,
                                     Instant startedAt, Instant completedAt, long durationMs,
                                     String engineVersion, AxeAnalysisStatus status, List<String> warnings,
                                     AxeErrorCode errorCode, String errorMessage,
                                     List<AxeRuleResult> violations, List<AxeRuleResult> incomplete,
                                     List<AxeRuleResult> passes, List<AxeRuleResult> inapplicable,
                                     List<ArtifactReference> artifacts) {
        return new AxeAnalysisResult(command.analysisId(), command.runId(), snapshot.snapshotId(),
                snapshot.viewport(), snapshot.finalUrl(), snapshot.contentHash(), properties.getProfileVersion(),
                properties.getTags(), properties.getAdapterVersion(), engineVersion, startedAt, completedAt,
                durationMs, properties.getTimeout().toMillis(), status, warnings, errorCode, errorMessage,
                violations, incomplete, passes, inapplicable, artifacts);
    }

    private Map<String, Object> metadata(RunAxeAnalysisCommand command, PageSnapshot snapshot,
                                         Instant startedAt, Instant completedAt, long durationMs,
                                         String engineVersion, AxeAnalysisStatus status, List<String> warnings,
                                         ArtifactReference rawArtifact) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("analysisId", command.analysisId());
        values.put("runId", command.runId());
        values.put("snapshotId", snapshot.snapshotId());
        values.put("viewport", snapshot.viewport());
        values.put("finalUrl", snapshot.finalUrl());
        values.put("contentHash", snapshot.contentHash());
        values.put("profileVersion", properties.getProfileVersion());
        values.put("tags", properties.getTags());
        values.put("adapterVersion", properties.getAdapterVersion());
        values.put("engineVersion", engineVersion);
        values.put("startedAt", startedAt);
        values.put("completedAt", completedAt);
        values.put("durationMs", durationMs);
        values.put("timeoutMs", properties.getTimeout().toMillis());
        values.put("status", status);
        values.put("warnings", warnings);
        values.put("rawResult", rawArtifact);
        return values;
    }

    private List<AxeRuleResult> mapRules(List<Rule> rules) {
        if (rules == null) return List.of();
        return rules.stream().map(rule -> new AxeRuleResult(rule.getId(), rule.getImpact(), rule.getTags(),
                rule.getDescription(), rule.getHelp(), rule.getHelpUrl(), mapNodes(rule.getNodes()))).toList();
    }

    private List<AxeNodeResult> mapNodes(List<CheckedNode> nodes) {
        if (nodes == null) return List.of();
        return nodes.stream().map(node -> new AxeNodeResult(node.getTarget(), node.getHtml(),
                node.getFailureSummary(), node.getImpact(), mapChecks(node.getAny()), mapChecks(node.getAll()),
                mapChecks(node.getNone()))).toList();
    }

    private List<AxeCheckResult> mapChecks(List<Check> checks) {
        if (checks == null) return List.of();
        return checks.stream().map(check -> new AxeCheckResult(check.getId(), check.getImpact(),
                check.getMessage(), check.getData(), mapRelatedNodes(check.getRelatedNodes()))).toList();
    }

    private List<AxeRelatedNode> mapRelatedNodes(List<Node> nodes) {
        if (nodes == null) return List.of();
        return nodes.stream().map(node -> new AxeRelatedNode(node.getTarget(), node.getHtml())).toList();
    }

    private boolean looksLikeInjectionFailure(RuntimeException exception) {
        String message = String.valueOf(exception.getMessage()).toLowerCase();
        return message.contains("inject") || message.contains("axe.min.js") || message.contains("script");
    }

    private String concise(String message) {
        if (message == null || message.isBlank()) return "Eroare axe-core nespecificată.";
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
