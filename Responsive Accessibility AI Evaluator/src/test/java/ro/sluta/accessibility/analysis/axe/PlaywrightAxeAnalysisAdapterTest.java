package ro.sluta.accessibility.analysis.axe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Check;
import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Node;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.results.TestEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import ro.sluta.accessibility.browser.ArtifactWriteException;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.browser.StabilizedBrowserPagePort;
import ro.sluta.accessibility.config.AxeAnalysisProperties;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.SnapshotStatus;
import ro.sluta.accessibility.domain.Viewport;

class PlaywrightAxeAnalysisAdapterTest {
    @TempDir Path storage;

    @Test
    void mapsEveryCategoryAndPreservesTargetsChecksAndRelatedNodes() {
        AxeResults raw = validResults();
        AxeEngine engine = engineReturning(raw);
        PlaywrightAxeAnalysisAdapter adapter = adapter(engine, new AtomicArtifactStore(storage));

        AxeAnalysisResult result = adapter.analyzeStabilizedPage(command(), mock(Page.class), snapshot());

        assertThat(result.status()).isEqualTo(AxeAnalysisStatus.SUCCESS);
        assertThat(result.violations()).hasSize(1);
        assertThat(result.incomplete()).hasSize(1);
        assertThat(result.passes()).hasSize(1);
        assertThat(result.inapplicable()).hasSize(1);
        AxeNodeResult node = result.violations().getFirst().nodes().getFirst();
        assertThat(node.target()).isEqualTo(List.of("#target"));
        assertThat(node.html()).contains("button");
        assertThat(node.failureSummary()).contains("Fix");
        assertThat(node.any()).singleElement().satisfies(check -> {
            assertThat(check.id()).isEqualTo("has-visible-text");
            assertThat(check.data()).isEqualTo(Map.of("source", "fixture"));
            assertThat(check.relatedNodes()).singleElement()
                    .satisfies(related -> assertThat(related.target()).isEqualTo(List.of("#label")));
        });
    }

    @Test
    void reportsInvalidResultAndTimeoutWithStructuredCodes() {
        AxeResults invalid = new AxeResults();
        invalid.setViolations(List.of());
        invalid.setIncomplete(List.of());
        invalid.setPasses(List.of());
        invalid.setInapplicable(List.of());
        AxeAnalysisResult invalidResult = adapter(engineReturning(invalid), new AtomicArtifactStore(storage))
                .analyzeStabilizedPage(command(), mock(Page.class), snapshot());

        AxeEngine timeoutEngine = new AxeEngine() {
            @Override public AxeResults analyze(Page page, List<String> tags) {
                throw new TimeoutError("fixture timeout");
            }
            @Override public String adapterVersion() { return "4.13.0"; }
        };
        AxeAnalysisResult timeout = adapter(timeoutEngine, new AtomicArtifactStore(storage))
                .analyzeStabilizedPage(command(), mock(Page.class), snapshot());

        assertThat(invalidResult.status()).isEqualTo(AxeAnalysisStatus.FAILED);
        assertThat(invalidResult.errorCode()).isEqualTo(AxeErrorCode.AXE_INVALID_RESULT);
        assertThat(timeout.status()).isEqualTo(AxeAnalysisStatus.FAILED);
        assertThat(timeout.errorCode()).isEqualTo(AxeErrorCode.AXE_EXECUTION_TIMEOUT);
    }

    @Test
    void reportsVersionMismatchBeforeExecution() {
        AxeEngine mismatched = new AxeEngine() {
            @Override public AxeResults analyze(Page page, List<String> tags) {
                throw new AssertionError("Motorul nu trebuie executat când versiunea este incompatibilă.");
            }
            @Override public String adapterVersion() { return "4.12.0"; }
        };

        AxeAnalysisResult result = adapter(mismatched, new AtomicArtifactStore(storage))
                .analyzeStabilizedPage(command(), mock(Page.class), snapshot());

        assertThat(result.errorCode()).isEqualTo(AxeErrorCode.AXE_VERSION_MISMATCH);
    }

    @Test
    void reportsAtomicWriteFailureWithoutInventingAValidResult() {
        AtomicArtifactStore failingStore = mock(AtomicArtifactStore.class);
        when(failingStore.writeRunArtifact(any(), anyString(), anyString(), any(), anyString(), any(), anyString()))
                .thenThrow(new ArtifactWriteException("write failed", new IOException("fixture")));

        AxeAnalysisResult result = adapter(engineReturning(validResults()), failingStore)
                .analyzeStabilizedPage(command(), mock(Page.class), snapshot());

        assertThat(result.status()).isEqualTo(AxeAnalysisStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(AxeErrorCode.AXE_RESULT_WRITE_FAILED);
        assertThat(result.artifacts()).isEmpty();
    }

    private PlaywrightAxeAnalysisAdapter adapter(AxeEngine engine, AtomicArtifactStore store) {
        AxeAnalysisProperties properties = new AxeAnalysisProperties();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new PlaywrightAxeAnalysisAdapter(mock(StabilizedBrowserPagePort.class), engine,
                properties, store, mapper);
    }

    private AxeEngine engineReturning(AxeResults results) {
        return new AxeEngine() {
            @Override public AxeResults analyze(Page page, List<String> tags) { return results; }
            @Override public String adapterVersion() { return "4.13.0"; }
        };
    }

    private AxeResults validResults() {
        TestEngine testEngine = new TestEngine();
        testEngine.setName("axe-core");
        ReflectionTestUtils.setField(testEngine, "version", "4.13.0");

        Node related = new Node();
        related.setTarget(List.of("#label"));
        related.setHtml("<span id='label'>Nume</span>");
        Check check = new Check();
        check.setId("has-visible-text");
        check.setImpact("serious");
        check.setMessage("Elementul nu are text vizibil.");
        check.setData(Map.of("source", "fixture"));
        check.setRelatedNodes(List.of(related));
        CheckedNode node = new CheckedNode();
        node.setTarget(List.of("#target"));
        node.setHtml("<button id='target'></button>");
        node.setImpact("serious");
        node.setFailureSummary("Fix any of the following");
        node.setAny(List.of(check));
        node.setAll(List.of());
        node.setNone(List.of());

        Rule rule = new Rule();
        rule.setId("button-name");
        rule.setImpact("serious");
        rule.setTags(List.of("wcag2a", "wcag412"));
        rule.setDescription("Ensure buttons have discernible text");
        rule.setHelp("Buttons must have discernible text");
        rule.setHelpUrl("https://dequeuniversity.com/rules/axe/button-name");
        rule.setNodes(List.of(node));

        AxeResults results = new AxeResults();
        results.setTestEngine(testEngine);
        results.setViolations(List.of(rule));
        results.setIncomplete(List.of(rule));
        results.setPasses(List.of(rule));
        results.setInapplicable(List.of(rule));
        return results;
    }

    private RunAxeAnalysisCommand command() {
        return new RunAxeAnalysisCommand(UUID.randomUUID(), UUID.randomUUID(),
                "https://example.com", Viewport.DESKTOP, "none");
    }

    private PageSnapshot snapshot() {
        return new PageSnapshot(UUID.randomUUID(), UUID.randomUUID(), URI.create("https://example.com"),
                URI.create("https://example.com"), Instant.now(), Viewport.DESKTOP, null, null, null,
                List.of(), List.of(), SnapshotStatus.SUCCESS, List.of(), null, null, "f2-v1",
                "a".repeat(64), "none@1", List.of());
    }
}
