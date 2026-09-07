package ro.sluta.accessibility.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ro.sluta.accessibility.analysis.ai.*;
import ro.sluta.accessibility.browser.ArtifactContentReader;
import ro.sluta.accessibility.browser.ArtifactWriteException;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.config.AiAnalysisProperties;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.ArtifactType;

@Service
public class DefaultRunAiAnalysisService implements RunAiAnalysisUseCase {
    private final AiAnalysisProperties properties;
    private final PageContextBuilderV1 contextBuilder;
    private final ArtifactContentReader reader;
    private final AiContractResources resources;
    private final AiAnalysisPort provider;
    private final AiResponseValidator validator;
    private final AiCostCalculator costs;
    private final AtomicArtifactStore store;
    private final ObjectMapper objectMapper;

    public DefaultRunAiAnalysisService(AiAnalysisProperties properties, PageContextBuilderV1 contextBuilder,
            ArtifactContentReader reader, AiContractResources resources, AiAnalysisPort provider,
            AiResponseValidator validator, AiCostCalculator costs, AtomicArtifactStore store, ObjectMapper objectMapper) {
        this.properties = properties;
        this.contextBuilder = contextBuilder;
        this.reader = reader;
        this.resources = resources;
        this.provider = provider;
        this.validator = validator;
        this.costs = costs;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAnalysisResult run(RunAiAnalysisCommand command) {
        AiAnalysisProperties.Model model = properties.models().get(command.modelId());
        if (model == null || !model.enabled()) return failure(command, model, AiErrorCode.AI_MODEL_DISABLED, "Modelul AI nu este activ.", null, null, 0, List.of());
        if (command.condition() == AiCondition.AI_WITH_AXE_CONTEXT && !properties.exploratoryAxeContextEnabled())
            return failure(command, model, AiErrorCode.AI_MODEL_DISABLED, "Condiția exploratorie cu context axe este dezactivată.", null, null, 0, List.of());
        if (!command.paidActionConfirmed())
            return failure(command, model, AiErrorCode.AI_COST_LIMIT_EXCEEDED, "Apelul plătit nu a fost confirmat explicit.", null, null, 0, List.of());

        PageContext context = contextBuilder.build(command.snapshot());
        String screenshot = command.condition() == AiCondition.AI_MULTIMODAL ? screenshotDataUrl(command) : null;
        AiCost estimate = costs.estimate(context.retainedCharacters(), model, properties.maxOutputTokens());
        if (command.maximumCostUsd() != null && estimate.amountUsd().compareTo(command.maximumCostUsd()) > 0)
            return failure(command, model, AiErrorCode.AI_COST_LIMIT_EXCEEDED, "Estimarea depășește plafonul confirmat.", estimate, null, 0, List.of());

        List<ArtifactReference> artifacts = new ArrayList<>();
        AiProviderRequest providerRequest = new AiProviderRequest(model.providerModel(), command.condition(), resources.prompt(),
                context, screenshot, resources.schema(), properties.maxOutputTokens());
        try {
            artifacts.add(write(command, "request.json", ArtifactType.AI_REQUEST_JSON,
                    requestArtifact(command, providerRequest), properties.promptVersion()));
        } catch (ArtifactWriteException exception) {
            return failure(command, model, AiErrorCode.AI_ARTIFACT_WRITE_FAILED, exception.getMessage(), estimate, null, 0, artifacts);
        }

        AiProviderResponse last = null;
        AiErrorCode errorCode = null;
        String errorMessage = null;
        int attempt = 0;
        while (attempt <= command.profile().maxRetries()) {
            try {
                last = provider.analyze(providerRequest);
                artifacts.removeIf(a -> a.artifactType() == ArtifactType.AI_RESPONSE_JSON);
                artifacts.add(write(command, "response.json", ArtifactType.AI_RESPONSE_JSON,
                        last.rawResponse(), properties.schemaVersion()));
                if (last.refusal()) { errorCode = AiErrorCode.AI_REFUSAL; errorMessage = "Modelul a refuzat analiza."; break; }
                if (last.truncated()) { errorCode = AiErrorCode.AI_TRUNCATED; errorMessage = "Răspunsul a fost trunchiat."; break; }
                AiResponseValidator.Validation validation = validator.validate(last.outputText());
                if (validation.valid()) {
                    artifacts.add(write(command, "parsed.json", ArtifactType.AI_PARSED_JSON,
                            pretty(validation.parsed()), properties.schemaVersion()));
                    AiCost actual = costs.actual(last.usage(), model);
                    artifacts.add(metadata(command, model, context, estimate, actual, last, attempt, null, artifacts));
                    return success(command, model, validation.parsed(), estimate, actual, last, attempt, artifacts);
                }
                errorCode = last.outputText() == null || last.outputText().isBlank()
                        ? AiErrorCode.AI_RESPONSE_EMPTY : AiErrorCode.AI_SCHEMA_INVALID;
                errorMessage = validation.error();
                if (attempt >= command.profile().maxRetries()) break;
            } catch (AiProviderException exception) {
                errorCode = exception.code(); errorMessage = exception.getMessage();
                if (!exception.transientFailure() || attempt >= command.profile().maxRetries()) break;
            } catch (ArtifactWriteException exception) {
                errorCode = AiErrorCode.AI_ARTIFACT_WRITE_FAILED; errorMessage = exception.getMessage(); break;
            }
            attempt++;
        }
        AiCost actual = last == null ? null : costs.actual(last.usage(), model);
        try { artifacts.add(metadata(command, model, context, estimate, actual, last, attempt, errorCode, artifacts)); }
        catch (ArtifactWriteException exception) { errorCode = AiErrorCode.AI_ARTIFACT_WRITE_FAILED; errorMessage = exception.getMessage(); }
        return failure(command, model, errorCode, errorMessage, estimate, last, attempt, artifacts);
    }

    private String screenshotDataUrl(RunAiAnalysisCommand command) {
        ArtifactReference reference = command.snapshot().artifacts().stream()
                .filter(a -> a.artifactType() == ArtifactType.VIEWPORT_SCREENSHOT).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Snapshotul multimodal nu conține viewport.png."));
        return "data:" + reference.mimeType() + ";base64," + Base64.getEncoder().encodeToString(reader.readVerified(reference));
    }

    private ArtifactReference write(RunAiAnalysisCommand command, String file, ArtifactType type, String value, String version) {
        return store.writeRunArtifact(command.runId(), "ai", file, type, "application/json",
                value.getBytes(StandardCharsets.UTF_8), version);
    }

    private String requestArtifact(RunAiAnalysisCommand command, AiProviderRequest request) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        sanitized.put("runId", command.runId());
        sanitized.put("snapshotId", command.snapshot().snapshotId());
        sanitized.put("model", request.providerModel());
        sanitized.put("condition", request.condition());
        sanitized.put("promptVersion", properties.promptVersion());
        sanitized.put("schemaVersion", properties.schemaVersion());
        sanitized.put("contextVersion", request.context().version());
        sanitized.put("contextSha256", request.context().sha256());
        sanitized.put("contextCharacters", request.context().retainedCharacters());
        sanitized.put("contextTruncated", request.context().truncated());
        sanitized.put("screenshotIncluded", request.screenshotDataUrl() != null);
        sanitized.put("maxOutputTokens", request.maxOutputTokens());
        return pretty(sanitized);
    }

    private ArtifactReference metadata(RunAiAnalysisCommand command, AiAnalysisProperties.Model model, PageContext context,
            AiCost estimate, AiCost actual, AiProviderResponse response, int retries, AiErrorCode error,
            List<ArtifactReference> artifacts) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("modelId", command.modelId()); metadata.put("providerModel", model.providerModel());
        metadata.put("reportedModel", response == null ? null : response.reportedModel());
        metadata.put("condition", command.condition()); metadata.put("profile", command.profile());
        metadata.put("promptVersion", properties.promptVersion()); metadata.put("schemaVersion", properties.schemaVersion());
        metadata.put("contextVersion", context.version()); metadata.put("contextSha256", context.sha256());
        metadata.put("usage", response == null ? AiUsage.empty() : response.usage());
        metadata.put("latencyMs", response == null ? 0 : response.latencyMs());
        metadata.put("requestId", response == null ? null : response.requestId());
        metadata.put("responseId", response == null ? null : response.responseId());
        metadata.put("estimatedCostUsd", estimate.amountUsd()); metadata.put("actualCostUsd", actual == null ? null : actual.amountUsd());
        metadata.put("retryCount", retries); metadata.put("errorCode", error);
        metadata.put("artifactHashes", artifacts.stream().collect(java.util.stream.Collectors.toMap(
                ArtifactReference::relativePath, ArtifactReference::sha256, (a, b) -> b, LinkedHashMap::new)));
        return write(command, "metadata.json", ArtifactType.AI_METADATA_JSON, pretty(metadata), properties.schemaVersion());
    }

    private String pretty(Object value) {
        try { return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("JSON-ul AI nu poate fi serializat.", exception); }
    }

    private AiAnalysisResult success(RunAiAnalysisCommand c, AiAnalysisProperties.Model m, com.fasterxml.jackson.databind.JsonNode parsed,
            AiCost estimate, AiCost actual, AiProviderResponse r, int retries, List<ArtifactReference> artifacts) {
        return new AiAnalysisResult(c.runId(), c.snapshot().snapshotId(), c.modelId(), m.providerModel(), c.condition(), c.profile(),
                properties.promptVersion(), properties.schemaVersion(), properties.contextVersion(), true, parsed, r.usage(), actual,
                estimate, r.latencyMs(), retries, r.responseId(), r.requestId(), r.reportedModel(), null, null, artifacts);
    }

    private AiAnalysisResult failure(RunAiAnalysisCommand c, AiAnalysisProperties.Model m, AiErrorCode code, String message,
            AiCost estimate, AiProviderResponse r, int retries, List<ArtifactReference> artifacts) {
        return new AiAnalysisResult(c.runId(), c.snapshot().snapshotId(), c.modelId(), m == null ? null : m.providerModel(), c.condition(), c.profile(),
                properties.promptVersion(), properties.schemaVersion(), properties.contextVersion(), false, null,
                r == null ? AiUsage.empty() : r.usage(), r == null || m == null ? null : costs.actual(r.usage(), m), estimate,
                r == null ? 0 : r.latencyMs(), retries, r == null ? null : r.responseId(), r == null ? null : r.requestId(),
                r == null ? null : r.reportedModel(), code, message, artifacts);
    }
}
