package ro.sluta.accessibility.analysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.sluta.accessibility.application.DefaultRunAiAnalysisService;
import ro.sluta.accessibility.browser.ArtifactContentReader;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.domain.ArtifactType;

class DefaultRunAiAnalysisServiceTest {
    @TempDir Path root;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void appProfileRetriesInvalidSchemaAndPreservesAuditableArtifacts() throws Exception {
        FakeProvider provider = new FakeProvider(response("{\"invalid\":true}"), response(AiTestFixtures.VALID));
        var service = service(provider);
        UUID runId = UUID.randomUUID();
        var snapshot = AiTestFixtures.snapshot(root, runId);

        AiAnalysisResult result = service.run(new RunAiAnalysisCommand(runId, snapshot, "GPT5_NANO",
                AiCondition.AI_TEXT, AiRunProfile.APP_DEFAULT_V1, new BigDecimal("1"), true));

        assertThat(result.success()).isTrue();
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.usage()).isEqualTo(new AiUsage(100, 20, 30, 130));
        assertThat(result.cost().amountUsd()).isPositive();
        assertThat(result.providerRequestId()).isEqualTo("req_test");
        assertThat(provider.requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.condition()).isEqualTo(AiCondition.AI_TEXT);
            assertThat(request.screenshotDataUrl()).isNull();
            assertThat(request.context().text()).doesNotContain("axe-results");
        });
        assertThat(result.artifacts()).extracting(a -> a.artifactType()).contains(
                ArtifactType.AI_REQUEST_JSON, ArtifactType.AI_RESPONSE_JSON,
                ArtifactType.AI_PARSED_JSON, ArtifactType.AI_METADATA_JSON);
        for (var artifact : result.artifacts()) {
            assertThat(artifact.relativePath()).startsWith("storage/runs/" + runId + "/ai/");
            assertThat(AtomicArtifactStore.sha256(Files.readAllBytes(root.resolve(artifact.relativePath())))).isEqualTo(artifact.sha256());
        }
        String request = Files.readString(root.resolve(result.artifacts().stream()
                .filter(a -> a.artifactType() == ArtifactType.AI_REQUEST_JSON).findFirst().orElseThrow().relativePath()));
        assertThat(request).doesNotContainIgnoringCase("authorization").doesNotContain("data:image");
    }

    @Test void experimentDoesNotRetryAndKeepsInvalidRawResponse() throws Exception {
        FakeProvider provider = new FakeProvider(response("{\"invalid\":true}"), response(AiTestFixtures.VALID));
        UUID runId = UUID.randomUUID();
        AiAnalysisResult result = service(provider).run(new RunAiAnalysisCommand(runId,
                AiTestFixtures.snapshot(root, runId), "GPT5_NANO", AiCondition.AI_TEXT,
                AiRunProfile.EXPERIMENT_V1, BigDecimal.ONE, true));
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(AiErrorCode.AI_SCHEMA_INVALID);
        assertThat(result.retryCount()).isZero();
        assertThat(provider.requests).hasSize(1);
        Path raw = root.resolve(result.artifacts().stream().filter(a -> a.artifactType() == ArtifactType.AI_RESPONSE_JSON)
                .findFirst().orElseThrow().relativePath());
        assertThat(Files.readString(raw)).contains("invalid");
    }

    @Test void multimodalAddsOnlyViewportImageAndUnconfirmedCallsNeverReachProvider() {
        FakeProvider provider = new FakeProvider(response(AiTestFixtures.VALID));
        UUID runId = UUID.randomUUID();
        var snapshot = AiTestFixtures.snapshot(root, runId);
        AiAnalysisResult multimodal = service(provider).run(new RunAiAnalysisCommand(runId, snapshot, "GPT5_NANO",
                AiCondition.AI_MULTIMODAL, AiRunProfile.APP_DEFAULT_V1, BigDecimal.ONE, true));
        assertThat(multimodal.success()).isTrue();
        assertThat(provider.requests.getFirst().screenshotDataUrl()).startsWith("data:image/png;base64,");

        AiAnalysisResult blocked = service(provider).run(new RunAiAnalysisCommand(UUID.randomUUID(), snapshot, "GPT5_NANO",
                AiCondition.AI_TEXT, AiRunProfile.APP_DEFAULT_V1, BigDecimal.ONE, false));
        assertThat(blocked.errorCode()).isEqualTo(AiErrorCode.AI_COST_LIMIT_EXCEEDED);
        assertThat(provider.requests).hasSize(1);
    }

    private DefaultRunAiAnalysisService service(AiAnalysisPort provider) {
        var properties = AiTestFixtures.properties("http://unused", "server-key");
        var reader = new ArtifactContentReader(root);
        return new DefaultRunAiAnalysisService(properties, new PageContextBuilderV1(reader, properties), reader,
                new AiContractResources(properties), provider, new AiResponseValidator(mapper), new AiCostCalculator(),
                new AtomicArtifactStore(root), mapper);
    }

    private static AiProviderResponse response(String output) {
        return new AiProviderResponse(output, output, new AiUsage(100, 20, 30, 130),
                "resp_test", "req_test", "gpt-5-nano-2026-08-07", 25, false, false);
    }

    private static final class FakeProvider implements AiAnalysisPort {
        private final Deque<AiProviderResponse> responses = new ArrayDeque<>();
        private final List<AiProviderRequest> requests = new ArrayList<>();
        FakeProvider(AiProviderResponse... responses) { this.responses.addAll(List.of(responses)); }
        public AiProviderResponse analyze(AiProviderRequest request) { requests.add(request); return responses.removeFirst(); }
    }
}
