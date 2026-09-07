package ro.sluta.accessibility.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.application.CapturePageSnapshotUseCase;
import ro.sluta.accessibility.domain.ArtifactType;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.SnapshotErrorCode;
import ro.sluta.accessibility.domain.SnapshotStatus;
import ro.sluta.accessibility.domain.Viewport;
import ro.sluta.accessibility.security.NetworkAccessPolicy;

@SpringBootTest(properties = {
        "app.browser.limits.max-resources=8",
        "app.browser.limits.navigation-timeout=2s",
        "app.browser.limits.total-capture-timeout=15s",
        "app.browser.limits.interaction-step-timeout=500ms",
        "app.browser.stabilization.font-timeout=200ms",
        "app.browser.stabilization.final-delay=50ms"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaywrightCaptureIntegrationTest {
    private static final byte[] PIXEL = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nVQAAAAASUVORK5CYII=");

    @Autowired private CapturePageSnapshotUseCase captureUseCase;
    @Autowired private Path snapshotStorageRoot;
    @Autowired private NetworkAccessPolicy networkAccessPolicy;

    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;

    @BeforeAll
    void startFixtureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/fixture", exchange -> html(exchange, fixturePage(false)));
        server.createContext("/overflow", exchange -> html(exchange, fixturePage(true)));
        server.createContext("/interaction", exchange -> html(exchange, interactionPage()));
        server.createContext("/redirect", exchange -> redirect(exchange, "/fixture"));
        server.createContext("/redirect-chain", exchange -> {
            String[] segments = exchange.getRequestURI().getPath().split("/");
            int current = Integer.parseInt(segments[segments.length - 1]);
            redirect(exchange, "/redirect-chain/" + (current + 1));
        });
        server.createContext("/blocked-resource", exchange -> html(exchange,
                "<html><head><title>Blocked resource</title></head><body>" +
                        "<img src=\"http://192.168.1.10/internal.png\" alt=\"blocked\"></body></html>"));
        server.createContext("/many-resources", exchange -> {
            StringBuilder html = new StringBuilder("<html><head><title>Resources</title></head><body>");
            for (int index = 0; index < 14; index++) html.append("<img alt='' src='/pixel?i=").append(index).append("'>");
            html.append("</body></html>");
            html(exchange, html.toString());
        });
        server.createContext("/pixel", exchange -> bytes(exchange, 200, "image/png", PIXEL));
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(3_000); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            html(exchange, fixturePage(false));
        });
        server.createContext("/large", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(11L * 1024 * 1024));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/unavailable", exchange -> bytes(exchange, 503, "text/plain",
                "temporarily unavailable".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/cookie", exchange -> {
            String incoming = exchange.getRequestHeaders().getFirst("Cookie");
            exchange.getResponseHeaders().add("Set-Cookie", "capture-session=present; Path=/");
            html(exchange, "<html><head><title>Isolation</title></head><body>" +
                    (incoming == null ? "fresh-context" : "reused-context") + "</body></html>");
        });
        server.createContext("/popup-download", exchange -> html(exchange, """
                <html><head><title>Controlled side effects</title></head><body>
                <a data-testid="download" href="/file.txt" download>Download</a>
                <script>window.open('/popup', '_blank'); document.querySelector('[data-testid=download]').click();</script>
                </body></html>
                """));
        server.createContext("/popup", exchange -> html(exchange, "<html><body>popup</body></html>"));
        server.createContext("/file.txt", exchange -> bytes(exchange, 200, "text/plain",
                "download".getBytes(StandardCharsets.UTF_8)));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    void stopFixtureServer() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void capturesAllApprovedViewportsWithTheSameDeterministicDeviceIdentity() {
        PageSnapshot desktop = capture("/fixture", Viewport.DESKTOP, "none");
        PageSnapshot mobile = capture("/fixture", Viewport.MOBILE, "none");
        PageSnapshot reflow = capture("/fixture", Viewport.REFLOW_320, "none");

        assertThat(desktop.status()).as(desktop.errorCode() + ": " + desktop.errorMessage())
                .isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(mobile.status()).isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(reflow.status()).isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(desktop.page().viewportWidth()).isEqualTo(1366);
        assertThat(mobile.page().viewportWidth()).isEqualTo(390);
        assertThat(reflow.page().viewportWidth()).isEqualTo(320);
        assertThat(desktop.browser().locale()).isEqualTo("en-US");
        assertThat(desktop.browser().timezoneId()).isEqualTo("UTC");
        assertThat(desktop.browser().deviceScaleFactor()).isEqualTo(1.0);
        assertThat(desktop.browser().colorScheme()).isEqualTo("light");
        assertThat(desktop.browser().reducedMotion()).isEqualTo("reduce");
        assertThat(desktop.browser().headless()).isTrue();
        assertThat(desktop.browser().isolatedContext()).isTrue();
        assertThat(desktop.browser().serviceWorkersBlocked()).isTrue();
        assertThat(desktop.browser().downloadsBlocked()).isTrue();
        assertThat(desktop.browser().popupsBlocked()).isTrue();
        assertThat(desktop.browser().playwrightVersion()).isEqualTo("1.61.0");
        assertThat(desktop.browser().browserName()).isEqualTo("chromium");
        assertThat(desktop.browser().browserVersion()).isNotBlank();
        assertThat(mobile.browser().locale()).isEqualTo(desktop.browser().locale());
        assertThat(reflow.browser().timezoneId()).isEqualTo(desktop.browser().timezoneId());
    }

    @Test
    void writesAllFiveArtifactsWithVerifiableMetadataAndExtractsInteractiveElements() throws Exception {
        PageSnapshot snapshot = capture("/fixture", Viewport.DESKTOP, "none");

        assertThat(snapshot.artifacts()).extracting(reference -> reference.artifactType())
                .containsExactlyInAnyOrder(ArtifactType.PAGE_HTML, ArtifactType.ARIA_YAML,
                        ArtifactType.VIEWPORT_SCREENSHOT, ArtifactType.FULL_PAGE_SCREENSHOT,
                        ArtifactType.METADATA_JSON);
        assertThat(snapshot.contentHash()).hasSize(64);
        assertThat(snapshot.page().title()).isEqualTo("Fixture F2");
        assertThat(snapshot.page().language()).isEqualTo("ro");
        assertThat(snapshot.interactiveElements()).anySatisfy(element -> {
            assertThat(element.elementId()).isEqualTo("primary-link");
            assertThat(element.role()).isEqualTo("link");
            assertThat(element.accessibleName()).contains("Detalii");
            assertThat(element.focusable()).isTrue();
            assertThat(element.boundingBox().width()).isPositive();
        });
        for (var artifact : snapshot.artifacts()) {
            Path path = snapshotStorageRoot.resolve(artifact.relativePath());
            assertThat(path).exists();
            byte[] bytes = Files.readAllBytes(path);
            assertThat(bytes).hasSize((int) artifact.byteSize());
            assertThat(AtomicArtifactStore.sha256(bytes)).isEqualTo(artifact.sha256());
            assertThat(artifact.relativePath()).doesNotStartWith("/").doesNotContain("\\");
            assertThat(artifact.extractorVersion()).isEqualTo("f2-v1");
        }
    }

    @Test
    void detectsHorizontalOverflowAndExecutesOnlyRegisteredInteractionScenario() {
        PageSnapshot overflow = capture("/overflow", Viewport.REFLOW_320, "none");
        PageSnapshot interaction = capture("/interaction", Viewport.MOBILE, "fixture-reveal-v1");
        PageSnapshot failedInteraction = capture("/fixture", Viewport.MOBILE, "fixture-reveal-v1");

        assertThat(overflow.page().horizontalOverflow()).isTrue();
        assertThat(interaction.status()).isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(interaction.interactionState()).isEqualTo("fixture-reveal-v1@1");
        assertThat(interaction.interactionSteps()).hasSize(2).allMatch(step -> step.success());
        assertThat(failedInteraction.status()).isEqualTo(SnapshotStatus.PARTIAL);
        assertThat(failedInteraction.errorCode()).isEqualTo(SnapshotErrorCode.INTERACTION_FAILED);
        assertThat(failedInteraction.interactionSteps()).hasSize(1).allMatch(step -> !step.success());
    }

    @Test
    void followsAllowedRedirectAndBlocksAfterTheApprovedLimit() {
        PageSnapshot allowed = capture("/redirect", Viewport.DESKTOP, "none");
        PageSnapshot blocked = capture("/redirect-chain/0", Viewport.DESKTOP, "none");

        assertThat(allowed.status()).isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(allowed.navigation().redirectCount()).isEqualTo(1);
        assertThat(allowed.finalUrl().getPath()).isEqualTo("/fixture");
        assertThat(blocked.status()).as(blocked.errorCode() + ": " + blocked.errorMessage()
                        + "; redirects=" + blocked.navigation().redirectCount())
                .isEqualTo(SnapshotStatus.BLOCKED);
        assertThat(blocked.errorCode()).isEqualTo(SnapshotErrorCode.REDIRECT_LIMIT_EXCEEDED);
        assertThat(blocked.navigation().redirectCount()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void blocksUnsafeSecondaryRequestAndAdditionalResourcesAfterTheLimit() {
        PageSnapshot blockedRequest = capture("/blocked-resource", Viewport.DESKTOP, "none");
        PageSnapshot resourceLimit = capture("/many-resources", Viewport.DESKTOP, "none");

        assertThat(blockedRequest.status()).isEqualTo(SnapshotStatus.PARTIAL);
        assertThat(blockedRequest.errorCode()).isEqualTo(SnapshotErrorCode.BLOCKED_REQUEST);
        assertThat(resourceLimit.status()).isEqualTo(SnapshotStatus.PARTIAL);
        assertThat(resourceLimit.errorCode()).isEqualTo(SnapshotErrorCode.RESOURCE_LIMIT_EXCEEDED);
        assertThat(resourceLimit.navigation().resourceCount()).isGreaterThan(8);
    }

    @Test
    void reportsTimeoutOversizedDocumentAndUnavailablePageWithStructuredCodes() throws Exception {
        PageSnapshot timeout = capture("/slow", Viewport.DESKTOP, "none");
        PageSnapshot tooLarge = capture("/large", Viewport.DESKTOP, "none");
        PageSnapshot unavailable = capture("/unavailable", Viewport.DESKTOP, "none");

        assertThat(timeout.status()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(timeout.errorCode()).isEqualTo(SnapshotErrorCode.PAGE_LOAD_TIMEOUT);
        assertThat(tooLarge.status()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(tooLarge.errorCode()).isEqualTo(SnapshotErrorCode.MAIN_DOCUMENT_TOO_LARGE);
        assertThat(unavailable.status()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(unavailable.errorCode()).isEqualTo(SnapshotErrorCode.PAGE_UNAVAILABLE);
    }

    @Test
    void createsFreshContextPerRunAndControlsPopupAndDownloadSideEffects() throws Exception {
        PageSnapshot first = capture("/cookie", Viewport.DESKTOP, "none");
        PageSnapshot second = capture("/cookie", Viewport.DESKTOP, "none");
        PageSnapshot sideEffects = capture("/popup-download", Viewport.DESKTOP, "none");

        assertThat(readArtifact(first, ArtifactType.PAGE_HTML)).contains("fresh-context").doesNotContain("reused-context");
        assertThat(readArtifact(second, ArtifactType.PAGE_HTML)).contains("fresh-context").doesNotContain("reused-context");
        assertThat(sideEffects.status()).isIn(SnapshotStatus.SUCCESS, SnapshotStatus.PARTIAL);
        assertThat(sideEffects.browser().popupsBlocked()).isTrue();
        assertThat(sideEffects.browser().downloadsBlocked()).isTrue();
        assertThat(networkAccessPolicy.allowsLoopbackFixtures()).isTrue();
    }

    private PageSnapshot capture(String path, Viewport viewport, String scenario) {
        return captureUseCase.capture(new CapturePageSnapshotCommand(
                UUID.randomUUID(), baseUrl + path, viewport, scenario));
    }

    private String readArtifact(PageSnapshot snapshot, ArtifactType type) throws IOException {
        var artifact = snapshot.artifacts().stream().filter(item -> item.artifactType() == type).findFirst().orElseThrow();
        return Files.readString(snapshotStorageRoot.resolve(artifact.relativePath()));
    }

    private String fixturePage(boolean overflow) {
        return """
                <!doctype html><html lang="ro"><head><meta charset="utf-8"><title>Fixture F2</title>
                <style>body{font-family:sans-serif;margin:16px}.wide{width:1000px}</style></head><body>
                <h1>Fixture local</h1><a id="primary-link" data-testid="primary-link" href="#details">Detalii fixture</a>
                <button data-testid="action">Acțiune</button><input aria-label="Căutare">
                %s<div id="details">Conținut controlat</div></body></html>
                """.formatted(overflow ? "<div class=\"wide\">Conținut lat</div>" : "");
    }

    private String interactionPage() {
        return """
                <!doctype html><html lang="ro"><head><title>Interaction fixture</title></head><body>
                <button data-testid="reveal" onclick="document.querySelector('[data-testid=revealed-panel]').hidden=false">Arată</button>
                <section data-testid="revealed-panel" hidden>Panou vizibil</section>
                <form onsubmit="event.preventDefault();document.querySelector('[data-testid=submitted]').hidden=false">
                <label>Nume <input data-testid="name"></label>
                <select data-testid="category"><option value="accessibility">Accesibilitate</option></select>
                <button data-testid="submit">Trimite</button><p data-testid="submitted" hidden>Trimis</p></form>
                </body></html>
                """;
    }

    private void html(HttpExchange exchange, String html) throws IOException {
        bytes(exchange, 200, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void bytes(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

}
