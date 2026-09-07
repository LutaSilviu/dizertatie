package ro.sluta.accessibility.analysis.ai;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.config.AiAnalysisProperties;
import ro.sluta.accessibility.domain.*;

final class AiTestFixtures {
    static final String VALID = """
            {"analysisSummary":"Rezumat","limitations":[],"findings":[{"title":"Imagine fără alternativă","description":"Descriere","wcagCriteria":["1.1.1"],"conformanceLevel":"A","severity":"SERIOUS","element":{"selector":"img","htmlSnippet":"<img>","role":"img","accessibleName":null,"visibleText":null},"explanation":"Explicație","impact":"Impact","recommendation":"Adăugați alt.","confidence":0.9,"evidence":["Element img"]}]}
            """;

    static AiAnalysisProperties properties(String baseUrl, String apiKey) {
        var model = new AiAnalysisProperties.Model("gpt-5-nano", "GPT-5 nano", true,
                List.of("TEXT", "IMAGE"), List.of("MAX_OUTPUT_TOKENS", "JSON_SCHEMA"),
                new BigDecimal("0.05"), new BigDecimal("0.005"), new BigDecimal("0.40"),
                LocalDate.of(2026, 9, 1));
        return new AiAnalysisProperties(baseUrl, apiKey, Duration.ofSeconds(5), 8000, 150_000,
                false, "PROMPT_ACCESSIBILITY_V1", "AI_FINDINGS_SCHEMA_V1", "PAGE_CONTEXT_V1",
                Map.of("GPT5_NANO", model));
    }

    static PageSnapshot snapshot(Path root, UUID runId) {
        AtomicArtifactStore store = new AtomicArtifactStore(root);
        UUID snapshotId = UUID.randomUUID();
        List<ArtifactReference> artifacts = new ArrayList<>();
        artifacts.add(store.write(runId, snapshotId, "page.html", ArtifactType.PAGE_HTML, "text/html",
                "<html><body><script>secret()</script><img data-token=abc></body></html>".getBytes(StandardCharsets.UTF_8), "f2-v1"));
        artifacts.add(store.write(runId, snapshotId, "aria.yaml", ArtifactType.ARIA_YAML, "text/yaml",
                "document:\n  img: unnamed".getBytes(StandardCharsets.UTF_8), "f2-v1"));
        artifacts.add(store.write(runId, snapshotId, "viewport.png", ArtifactType.VIEWPORT_SCREENSHOT, "image/png",
                new byte[]{1, 2, 3, 4}, "f2-v1"));
        return new PageSnapshot(snapshotId, runId, URI.create("https://example.com"), URI.create("https://example.com"),
                Instant.now(), Viewport.DESKTOP, null, null, null, artifacts, List.of(), SnapshotStatus.SUCCESS,
                List.of(), null, null, "f2-v1", "a".repeat(64), "none@1", List.of());
    }

    private AiTestFixtures() { }
}
