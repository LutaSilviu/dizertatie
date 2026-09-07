package ro.sluta.accessibility.analysis.axe;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.domain.ArtifactType;
import ro.sluta.accessibility.domain.SnapshotStatus;
import ro.sluta.accessibility.domain.Viewport;

@SpringBootTest(properties = {
        "app.browser.limits.max-resources=50",
        "app.browser.limits.navigation-timeout=3s",
        "app.browser.limits.total-capture-timeout=20s",
        "app.browser.stabilization.font-timeout=200ms",
        "app.browser.stabilization.final-delay=50ms",
        "app.axe.timeout=10s"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AxeAnalysisIntegrationTest {
    @Autowired private RunAxeAnalysisUseCase useCase;
    @Autowired private Path snapshotStorageRoot;

    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    private final AtomicInteger badDocumentGets = new AtomicInteger();

    @BeforeAll
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/bad", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) badDocumentGets.incrementAndGet();
            html(exchange, """
                    <!doctype html><html lang="ro"><head><title>Axe BAD</title></head><body>
                    <h1>Fixture BAD</h1><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
                    <img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
                    <input type="text"><button></button></body></html>
                    """);
        });
        server.createContext("/good", exchange -> html(exchange, """
                <!doctype html><html lang="ro"><head><title>Axe GOOD</title></head><body>
                <h1>Fixture GOOD</h1><img alt="Element decorativ" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
                <img alt="A doua imagine" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
                <label for="name">Nume</label><input id="name" type="text"><button>Trimite</button></body></html>
                """));
        server.createContext("/complex", exchange -> html(exchange, """
                <!doctype html><html lang="ro"><head><title>Shadow și frame</title></head><body>
                <h1>Fixture complex</h1><test-control></test-control><iframe title="Conținut local" src="/frame"></iframe>
                <script>customElements.define('test-control', class extends HTMLElement {
                  connectedCallback(){this.attachShadow({mode:'open'}).innerHTML='<button></button>';}
                });</script></body></html>
                """));
        server.createContext("/frame", exchange -> html(exchange, """
                <!doctype html><html lang="ro"><head><title>Frame</title></head><body>
                <img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=="></body></html>
                """));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    void stopServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void analyzesTheSameCapturedPageWithTheFrozenProfileAndPreservesArtifacts() throws Exception {
        badDocumentGets.set(0);
        AxeAnalysisExecution execution = analyze("/bad");

        assertThat(execution.snapshot().status()).isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(execution.axeResult().status()).as(execution.axeResult().errorMessage())
                .isEqualTo(AxeAnalysisStatus.SUCCESS);
        assertThat(execution.axeResult().profileVersion()).isEqualTo("WCAG_22_AA_V1");
        assertThat(execution.axeResult().tags()).containsExactly(
                "wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
        assertThat(execution.axeResult().adapterVersion()).isEqualTo("4.13.0");
        assertThat(execution.axeResult().engineVersion()).isNotBlank();
        assertThat(execution.axeResult().snapshotId()).isEqualTo(execution.snapshot().snapshotId());
        assertThat(execution.axeResult().contentHash()).isEqualTo(execution.snapshot().contentHash());
        assertThat(badDocumentGets).hasValue(1);

        assertThat(execution.axeResult().artifacts()).extracting(item -> item.artifactType())
                .containsExactly(ArtifactType.AXE_RESULTS_JSON, ArtifactType.AXE_METADATA_JSON);
        for (var artifact : execution.axeResult().artifacts()) {
            Path path = snapshotStorageRoot.resolve(artifact.relativePath());
            assertThat(path).exists();
            byte[] bytes = Files.readAllBytes(path);
            assertThat(bytes).hasSize((int) artifact.byteSize());
            assertThat(AtomicArtifactStore.sha256(bytes)).isEqualTo(artifact.sha256());
            assertThat(artifact.relativePath()).startsWith("storage/runs/").contains("/axe/");
        }
        String raw = read(execution, ArtifactType.AXE_RESULTS_JSON);
        assertThat(raw).contains("\"violations\"").contains("\"incomplete\"")
                .contains("\"passes\"").contains("\"inapplicable\"");
    }

    @Test
    void distinguishesBadAndGoodForAltLabelAndAccessibleNameAndKeepsMultipleNodes() {
        AxeAnalysisResult bad = analyze("/bad").axeResult();
        AxeAnalysisResult good = analyze("/good").axeResult();

        assertThat(ruleIds(bad)).contains("image-alt", "label", "button-name");
        assertThat(bad.violations()).filteredOn(rule -> rule.id().equals("image-alt"))
                .singleElement().satisfies(rule -> assertThat(rule.nodes()).hasSize(2));
        assertThat(ruleIds(good)).doesNotContain("image-alt", "label", "button-name");
        assertThat(bad.violationNodeCount()).isGreaterThan(good.violationNodeCount());
    }

    @Test
    void includesOpenShadowDomAndSameOriginIframeInTheLocalAnalysis() {
        AxeAnalysisResult result = analyze("/complex").axeResult();

        assertThat(result.status()).as(result.errorMessage()).isEqualTo(AxeAnalysisStatus.SUCCESS);
        assertThat(ruleIds(result)).contains("button-name", "image-alt");
        assertThat(result.violations().stream().flatMap(rule -> rule.nodes().stream()).map(Object::toString))
                .anyMatch(value -> value.contains("test-control"))
                .anyMatch(value -> value.contains("iframe") || value.contains("img"));
    }

    private AxeAnalysisExecution analyze(String path) {
        return useCase.analyze(new RunAxeAnalysisCommand(UUID.randomUUID(), UUID.randomUUID(),
                baseUrl + path, Viewport.DESKTOP, "none"));
    }

    private java.util.List<String> ruleIds(AxeAnalysisResult result) {
        return result.violations().stream().map(AxeRuleResult::id).toList();
    }

    private String read(AxeAnalysisExecution execution, ArtifactType type) throws IOException {
        var artifact = execution.axeResult().artifacts().stream()
                .filter(item -> item.artifactType() == type).findFirst().orElseThrow();
        return Files.readString(snapshotStorageRoot.resolve(artifact.relativePath()));
    }

    private void html(HttpExchange exchange, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
