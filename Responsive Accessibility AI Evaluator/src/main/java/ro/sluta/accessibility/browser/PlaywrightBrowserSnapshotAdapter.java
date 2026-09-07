package ro.sluta.accessibility.browser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.ReducedMotion;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.browser.interaction.InteractionExecutor;
import ro.sluta.accessibility.browser.interaction.InteractionScenario;
import ro.sluta.accessibility.config.BrowserCaptureProperties;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.ArtifactType;
import ro.sluta.accessibility.domain.BrowserMetadata;
import ro.sluta.accessibility.domain.InteractionStepResult;
import ro.sluta.accessibility.domain.InteractiveElement;
import ro.sluta.accessibility.domain.NavigationMetadata;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.SnapshotErrorCode;
import ro.sluta.accessibility.domain.SnapshotPageMetadata;
import ro.sluta.accessibility.domain.SnapshotStatus;
import ro.sluta.accessibility.security.NetworkPolicyException;

@Component
public class PlaywrightBrowserSnapshotAdapter implements BrowserSnapshotPort, StabilizedBrowserPagePort {
    static final String LOCALE = "en-US";
    static final String TIMEZONE = "UTC";
    static final double DEVICE_SCALE_FACTOR = 1.0;

    private final BrowserCaptureProperties properties;
    private final AtomicArtifactStore artifactStore;
    private final InteractionExecutor interactionExecutor;
    private final InteractiveElementExtractor interactiveExtractor;
    private final PageStabilizer stabilizer;
    private final ObjectMapper objectMapper;
    private final SafeRedirectResolver redirectResolver;

    public PlaywrightBrowserSnapshotAdapter(BrowserCaptureProperties properties,
                                            AtomicArtifactStore artifactStore,
                                            InteractionExecutor interactionExecutor,
                                            InteractiveElementExtractor interactiveExtractor,
                                            PageStabilizer stabilizer,
                                            ObjectMapper objectMapper,
                                            SafeRedirectResolver redirectResolver) {
        this.properties = properties;
        this.artifactStore = artifactStore;
        this.interactionExecutor = interactionExecutor;
        this.interactiveExtractor = interactiveExtractor;
        this.stabilizer = stabilizer;
        this.objectMapper = objectMapper;
        this.redirectResolver = redirectResolver;
    }

    @Override
    public PageSnapshot capture(CapturePageSnapshotCommand command, URI validatedUrl, InteractionScenario scenario) {
        return captureAndApply(command, validatedUrl, scenario, (page, snapshot) -> null).snapshot();
    }

    @Override
    public <T> BrowserPageOperationResult<T> captureAndApply(CapturePageSnapshotCommand command,
                                                            URI validatedUrl,
                                                            InteractionScenario scenario,
                                                            StabilizedPageOperation<T> operation) {
        UUID snapshotId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        Deadline deadline = new Deadline(properties.getLimits().getTotalCaptureTimeout());
        CaptureNetworkState network = new CaptureNetworkState();
        List<String> warnings = new ArrayList<>();
        List<ArtifactReference> artifacts = new ArrayList<>();
        List<InteractionStepResult> interactionResults = List.of();
        BrowserMetadata browserMetadata = null;
        NavigationMetadata navigationMetadata = null;
        SnapshotPageMetadata pageMetadata = null;
        URI finalUrl = null;
        String contentHash = null;
        SnapshotErrorCode recoverableError = null;
        String recoverableMessage = null;

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(properties.isHeadless()))) {
            browserMetadata = browserMetadata(browser);
            Browser.NewContextOptions contextOptions = contextOptions(command);
            try (BrowserContext context = browser.newContext(contextOptions)) {
                context.setDefaultNavigationTimeout(properties.getLimits().getNavigationTimeout().toMillis());
                context.setDefaultTimeout(properties.getLimits().getInteractionStepTimeout().toMillis());
                installWebSocketGuard(context, network);
                Page page = context.newPage();
                installHttpGuard(context, page, network);
                page.onPopup(Page::close);
                page.onDownload(Download::cancel);
                context.onPage(opened -> { if (opened != page) opened.close(); });
                context.onDialog(dialog -> dialog.dismiss());

                SafeRedirectResolver.ResolvedDestination destination;
                try {
                    destination = redirectResolver.resolve(validatedUrl, true,
                            properties.getLimits().getMaxRedirects(),
                            min(properties.getLimits().getNavigationTimeout(), deadline.remaining()),
                            properties.getLimits().getMaxMainDocumentBytes());
                    network.redirects.set(destination.redirectCount());
                } catch (NetworkPolicyException exception) {
                    return operationResult(terminal(command, snapshotId, validatedUrl, page, startedAt,
                            browserMetadata, exception.errorCode(), SnapshotStatus.BLOCKED,
                            exception.getMessage(), network));
                } catch (RedirectResolutionException exception) {
                    if (exception.errorCode() == SnapshotErrorCode.REDIRECT_LIMIT_EXCEEDED) {
                        network.redirects.set(properties.getLimits().getMaxRedirects() + 1);
                    }
                    SnapshotStatus status = exception.errorCode() == SnapshotErrorCode.REDIRECT_LIMIT_EXCEEDED
                            ? SnapshotStatus.BLOCKED : SnapshotStatus.FAILED;
                    return operationResult(terminal(command, snapshotId, validatedUrl, page, startedAt,
                            browserMetadata, exception.errorCode(), status, exception.getMessage(), network));
                }

                Response response;
                Instant domContentLoadedAt;
                try {
                    response = page.navigate(destination.uri().toString(), new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(deadline.timeoutMillis(properties.getLimits().getNavigationTimeout())));
                    domContentLoadedAt = Instant.now();
                } catch (PlaywrightException exception) {
                    if (network.terminalCode.get() != null) {
                        return operationResult(terminal(command, snapshotId, validatedUrl, page, startedAt,
                                browserMetadata, network, exception.getMessage()));
                    }
                    SnapshotErrorCode code = exception instanceof TimeoutError
                            ? SnapshotErrorCode.PAGE_LOAD_TIMEOUT : SnapshotErrorCode.PAGE_UNAVAILABLE;
                    return operationResult(terminal(command, snapshotId, validatedUrl, page, startedAt,
                            browserMetadata, code, SnapshotStatus.FAILED, concise(exception.getMessage()), network));
                }
                if (network.terminalCode.get() != null) {
                    return operationResult(terminal(command, snapshotId, validatedUrl, page, startedAt,
                            browserMetadata, network, network.terminalMessage.get()));
                }
                if (response == null || response.status() >= 400) {
                    return operationResult(terminal(command, snapshotId, validatedUrl, page, startedAt,
                            browserMetadata, SnapshotErrorCode.PAGE_UNAVAILABLE, SnapshotStatus.FAILED,
                            response == null ? "Pagina nu a returnat un răspuns HTTP." :
                                    "Pagina a răspuns cu HTTP " + response.status() + '.', network));
                }

                stabilizer.stabilize(page, properties.getStabilization().getFontTimeout(),
                        properties.getStabilization().getFinalDelay());
                deadline.requireTime();
                interactionResults = interactionExecutor.execute(page, scenario,
                        min(properties.getLimits().getInteractionStepTimeout(), deadline.remaining()));
                if (interactionResults.stream().anyMatch(result -> !result.success())) {
                    recoverableError = SnapshotErrorCode.INTERACTION_FAILED;
                    recoverableMessage = "Un pas al scenariului intern a eșuat.";
                    warnings.add(recoverableMessage);
                }
                if (!scenario.steps().isEmpty() && recoverableError == null) {
                    stabilizer.stabilize(page, properties.getStabilization().getFontTimeout(),
                            properties.getStabilization().getFinalDelay());
                }

                finalUrl = URI.create(page.url());
                Instant completedAt = Instant.now();
                navigationMetadata = new NavigationMetadata(startedAt, domContentLoadedAt, completedAt,
                        Duration.between(startedAt, completedAt).toMillis(), network.redirects.get(),
                        response.status(), network.resources.get());

                String html = page.content();
                String ariaYaml = page.ariaSnapshot(new Page.AriaSnapshotOptions()
                        .setTimeout(deadline.timeoutMillis(Duration.ofSeconds(5))));
                PageMeasurements measurements = pageMeasurements(page, response);
                pageMetadata = new SnapshotPageMetadata(page.title(), measurements.language(),
                        measurements.contentType(), measurements.documentWidth(), command.viewport().width(),
                        measurements.horizontalOverflow());
                List<InteractiveElement> interactiveElements = interactiveExtractor.extract(page);
                contentHash = AtomicArtifactStore.sha256((html + "\n---ARIA---\n" + ariaYaml + "\n" +
                        command.viewport().name() + "\n" + scenario.id() + '@' + scenario.version())
                        .getBytes(StandardCharsets.UTF_8));

                artifacts.add(artifactStore.write(command.runId(), snapshotId, "page.html",
                        ArtifactType.PAGE_HTML, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8),
                        properties.getExtractorVersion()));
                artifacts.add(artifactStore.write(command.runId(), snapshotId, "aria.yaml",
                        ArtifactType.ARIA_YAML, "application/yaml; charset=UTF-8",
                        ariaYaml.getBytes(StandardCharsets.UTF_8), properties.getExtractorVersion()));
                artifacts.add(artifactStore.write(command.runId(), snapshotId, "viewport.png",
                        ArtifactType.VIEWPORT_SCREENSHOT, "image/png",
                        page.screenshot(new Page.ScreenshotOptions().setFullPage(false)
                                .setTimeout(deadline.timeoutMillis(Duration.ofSeconds(10)))),
                        properties.getExtractorVersion()));
                try {
                    artifacts.add(artifactStore.write(command.runId(), snapshotId, "full-page.png",
                            ArtifactType.FULL_PAGE_SCREENSHOT, "image/png",
                            page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                                    .setTimeout(deadline.timeoutMillis(Duration.ofSeconds(10)))),
                            properties.getExtractorVersion()));
                } catch (RuntimeException exception) {
                    recoverableError = SnapshotErrorCode.SNAPSHOT_EXTRACTION_FAILED;
                    recoverableMessage = "Captura completă nu a putut fi generată: " + concise(exception.getMessage());
                    warnings.add(recoverableMessage);
                }

                if (network.recoverableCode.get() != null) {
                    recoverableError = network.recoverableCode.get();
                    recoverableMessage = network.recoverableMessage.get();
                    warnings.addAll(network.warnings);
                }
                SnapshotStatus status = recoverableError == null ? SnapshotStatus.SUCCESS : SnapshotStatus.PARTIAL;
                PageSnapshot provisional = snapshot(command, snapshotId, validatedUrl, finalUrl, browserMetadata,
                        navigationMetadata, pageMetadata, artifacts, interactiveElements, status, warnings,
                        recoverableError, recoverableMessage, contentHash, scenario, interactionResults);
                T appliedResult = operation.execute(page, provisional);
                try {
                    byte[] metadata = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(provisional);
                    artifacts.add(artifactStore.write(command.runId(), snapshotId, "metadata.json",
                            ArtifactType.METADATA_JSON, "application/json", metadata,
                            properties.getExtractorVersion()));
                } catch (JsonProcessingException | ArtifactWriteException exception) {
                    recoverableError = SnapshotErrorCode.ARTIFACT_WRITE_FAILED;
                    recoverableMessage = "metadata.json nu a putut fi publicat.";
                    warnings.add(recoverableMessage);
                    status = SnapshotStatus.PARTIAL;
                }
                PageSnapshot completedSnapshot = snapshot(command, snapshotId, validatedUrl, finalUrl, browserMetadata,
                        navigationMetadata, pageMetadata, artifacts, interactiveElements, status, warnings,
                        recoverableError, recoverableMessage, contentHash, scenario, interactionResults);
                return new BrowserPageOperationResult<>(completedSnapshot, appliedResult);
            }
        } catch (ArtifactWriteException exception) {
            SnapshotStatus status = artifacts.isEmpty() ? SnapshotStatus.FAILED : SnapshotStatus.PARTIAL;
            return operationResult(new PageSnapshot(snapshotId, command.runId(), validatedUrl, finalUrl, Instant.now(),
                    command.viewport(), browserMetadata, navigationMetadata, pageMetadata, artifacts, List.of(),
                    status, warnings, SnapshotErrorCode.ARTIFACT_WRITE_FAILED, concise(exception.getMessage()),
                    properties.getExtractorVersion(), contentHash, scenario.id() + '@' + scenario.version(),
                    interactionResults));
        } catch (PlaywrightException | IllegalStateException exception) {
            SnapshotStatus status = artifacts.isEmpty() ? SnapshotStatus.FAILED : SnapshotStatus.PARTIAL;
            SnapshotErrorCode code = exception instanceof TimeoutError
                    ? SnapshotErrorCode.PAGE_LOAD_TIMEOUT : SnapshotErrorCode.SNAPSHOT_EXTRACTION_FAILED;
            return operationResult(new PageSnapshot(snapshotId, command.runId(), validatedUrl, finalUrl, Instant.now(),
                    command.viewport(), browserMetadata, navigationMetadata, pageMetadata, artifacts, List.of(),
                    status, warnings, code, concise(exception.getMessage()), properties.getExtractorVersion(),
                    contentHash, scenario.id() + '@' + scenario.version(), interactionResults));
        }
    }

    private <T> BrowserPageOperationResult<T> operationResult(PageSnapshot snapshot) {
        return new BrowserPageOperationResult<>(snapshot, null);
    }

    private Browser.NewContextOptions contextOptions(CapturePageSnapshotCommand command) {
        return new Browser.NewContextOptions()
                .setViewportSize(command.viewport().width(), command.viewport().height())
                .setLocale(LOCALE).setTimezoneId(TIMEZONE).setDeviceScaleFactor(DEVICE_SCALE_FACTOR)
                .setColorScheme(ColorScheme.LIGHT).setReducedMotion(ReducedMotion.REDUCE)
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK).setAcceptDownloads(false)
                .setIgnoreHTTPSErrors(false).setIsMobile(false).setHasTouch(false);
    }

    private void installHttpGuard(BrowserContext context, Page page, CaptureNetworkState state) {
        context.route("**/*", route -> guardRequest(route, page, state));
    }

    private void installWebSocketGuard(BrowserContext context, CaptureNetworkState state) {
        context.routeWebSocket("**/*", webSocket -> {
            state.recoverable(SnapshotErrorCode.BLOCKED_REQUEST,
                    "O conexiune WebSocket a fost blocată: " + webSocket.url());
            webSocket.close();
        });
    }

    private void guardRequest(Route route, Page page, CaptureNetworkState state) {
        Request request = route.request();
        boolean mainDocument = isMainDocument(request, page);
        int count = state.resources.incrementAndGet();
        if (count > properties.getLimits().getMaxResources()) {
            state.recoverable(SnapshotErrorCode.RESOURCE_LIMIT_EXCEEDED,
                    "Limita de resurse a fost depășită; cererea a fost blocată.");
            route.abort("blockedbyclient");
            return;
        }
        try {
            URI requestUri = URI.create(request.url());
            SafeRedirectResolver.ResolvedDestination destination = redirectResolver.resolve(
                    requestUri, mainDocument, properties.getLimits().getMaxRedirects(),
                    properties.getLimits().getNavigationTimeout(),
                    mainDocument ? properties.getLimits().getMaxMainDocumentBytes() : Long.MAX_VALUE);
            if (mainDocument) state.redirects.set(Math.max(state.redirects.get(), destination.redirectCount()));
            if (destination.redirectCount() > 0) {
                int withRedirects = state.resources.addAndGet(destination.redirectCount());
                if (withRedirects > properties.getLimits().getMaxResources()) {
                    state.recoverable(SnapshotErrorCode.RESOURCE_LIMIT_EXCEEDED,
                            "Limita de resurse a fost depășită de un lanț de redirecționări.");
                    route.abort("blockedbyclient");
                    return;
                }
            }
            route.resume();
        } catch (TimeoutError exception) {
            if (mainDocument) state.terminal(SnapshotErrorCode.PAGE_LOAD_TIMEOUT,
                    "Navigarea documentului principal a depășit limita de timp.");
            else state.recoverable(SnapshotErrorCode.BLOCKED_REQUEST,
                    "O resursă secundară a depășit limita de timp și a fost blocată.");
            route.abort("timedout");
        } catch (NetworkPolicyException | RedirectResolutionException | IllegalArgumentException exception) {
            SnapshotErrorCode code = exception instanceof NetworkPolicyException policy ? policy.errorCode()
                    : exception instanceof RedirectResolutionException redirect ? redirect.errorCode()
                    : (mainDocument ? SnapshotErrorCode.BLOCKED_ADDRESS : SnapshotErrorCode.BLOCKED_REQUEST);
            if (mainDocument) state.terminal(code, concise(exception.getMessage()));
            else state.recoverable(SnapshotErrorCode.BLOCKED_REQUEST, concise(exception.getMessage()));
            route.abort("blockedbyclient");
        } catch (PlaywrightException exception) {
            if (mainDocument) state.terminal(SnapshotErrorCode.PAGE_UNAVAILABLE, concise(exception.getMessage()));
            else state.recoverable(SnapshotErrorCode.BLOCKED_REQUEST, concise(exception.getMessage()));
            route.abort("failed");
        }
    }

    private boolean isMainDocument(Request request, Page page) {
        try {
            return request.isNavigationRequest() && "document".equals(request.resourceType())
                    && request.frame() == page.mainFrame();
        } catch (PlaywrightException exception) {
            return request.isNavigationRequest() && "document".equals(request.resourceType());
        }
    }

    @SuppressWarnings("unchecked")
    private PageMeasurements pageMeasurements(Page page, Response response) {
        Map<String, Object> values = (Map<String, Object>) page.evaluate("""
                () => ({
                  language: document.documentElement.lang || '',
                  documentWidth: Math.max(document.documentElement.scrollWidth, document.body?.scrollWidth || 0),
                  viewportWidth: window.innerWidth,
                  horizontalOverflow: Math.max(document.documentElement.scrollWidth,
                    document.body?.scrollWidth || 0) > window.innerWidth + 1
                })
                """);
        String contentType = response.headers().getOrDefault("content-type", "");
        return new PageMeasurements(String.valueOf(values.getOrDefault("language", "")), contentType,
                ((Number) values.getOrDefault("documentWidth", 0)).doubleValue(),
                Boolean.TRUE.equals(values.get("horizontalOverflow")));
    }

    private BrowserMetadata browserMetadata(Browser browser) {
        String version = Playwright.class.getPackage().getImplementationVersion();
        if (version == null) version = "1.61.0";
        return new BrowserMetadata(version, browser.browserType().name(), browser.version(),
                properties.isHeadless(), LOCALE, TIMEZONE, DEVICE_SCALE_FACTOR, "light", "reduce",
                true, true, true, true);
    }

    private PageSnapshot snapshot(CapturePageSnapshotCommand command, UUID snapshotId, URI requested,
                                  URI finalUrl, BrowserMetadata browser, NavigationMetadata navigation,
                                  SnapshotPageMetadata page, List<ArtifactReference> artifacts,
                                  List<InteractiveElement> elements, SnapshotStatus status,
                                  List<String> warnings, SnapshotErrorCode errorCode, String errorMessage,
                                  String contentHash, InteractionScenario scenario,
                                  List<InteractionStepResult> steps) {
        return new PageSnapshot(snapshotId, command.runId(), requested, finalUrl, Instant.now(), command.viewport(),
                browser, navigation, page, artifacts, elements, status, warnings, errorCode, errorMessage,
                properties.getExtractorVersion(), contentHash, scenario.id() + '@' + scenario.version(), steps);
    }

    private PageSnapshot terminal(CapturePageSnapshotCommand command, UUID snapshotId, URI requested,
                                  Page page, Instant startedAt, BrowserMetadata browser,
                                  CaptureNetworkState network, String fallbackMessage) {
        SnapshotErrorCode code = network.terminalCode.get();
        SnapshotStatus status = code == SnapshotErrorCode.BLOCKED_ADDRESS
                || code == SnapshotErrorCode.BLOCKED_REQUEST
                || code == SnapshotErrorCode.REDIRECT_LIMIT_EXCEEDED
                || code == SnapshotErrorCode.DNS_RESOLUTION_FAILED
                ? SnapshotStatus.BLOCKED : SnapshotStatus.FAILED;
        return terminal(command, snapshotId, requested, page, startedAt, browser, code, status,
                network.terminalMessage.get() == null ? concise(fallbackMessage) : network.terminalMessage.get(), network);
    }

    private PageSnapshot terminal(CapturePageSnapshotCommand command, UUID snapshotId, URI requested,
                                  Page page, Instant startedAt, BrowserMetadata browser,
                                  SnapshotErrorCode code, SnapshotStatus status, String message,
                                  CaptureNetworkState network) {
        URI finalUri = null;
        try { if (page != null && !page.isClosed()) finalUri = URI.create(page.url()); } catch (RuntimeException ignored) { }
        Instant completed = Instant.now();
        NavigationMetadata navigation = new NavigationMetadata(startedAt, null, completed,
                Duration.between(startedAt, completed).toMillis(), network.redirects.get(), null,
                network.resources.get());
        return new PageSnapshot(snapshotId, command.runId(), requested, finalUri, completed, command.viewport(),
                browser, navigation, null, List.of(), List.of(), status, List.copyOf(network.warnings), code,
                message, properties.getExtractorVersion(), null, command.interactionScenarioId(), List.of());
    }

    private Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private String concise(String message) {
        if (message == null || message.isBlank()) return "Eroare browser nespecificată.";
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private record PageMeasurements(String language, String contentType, double documentWidth,
                                    boolean horizontalOverflow) { }

    private static final class CaptureNetworkState {
        private final AtomicInteger resources = new AtomicInteger();
        private final AtomicInteger redirects = new AtomicInteger();
        private final AtomicReference<SnapshotErrorCode> terminalCode = new AtomicReference<>();
        private final AtomicReference<String> terminalMessage = new AtomicReference<>();
        private final AtomicReference<SnapshotErrorCode> recoverableCode = new AtomicReference<>();
        private final AtomicReference<String> recoverableMessage = new AtomicReference<>();
        private final List<String> warnings = new ArrayList<>();

        private void terminal(SnapshotErrorCode code, String message) {
            terminalCode.compareAndSet(null, code);
            terminalMessage.compareAndSet(null, message);
        }

        private void recoverable(SnapshotErrorCode code, String message) {
            recoverableCode.compareAndSet(null, code);
            recoverableMessage.compareAndSet(null, message);
            if (!warnings.contains(message)) warnings.add(message);
        }

    }

    private static final class Deadline {
        private final long endNanos;
        private Deadline(Duration duration) { endNanos = System.nanoTime() + duration.toNanos(); }
        private Duration remaining() { return Duration.ofNanos(Math.max(0, endNanos - System.nanoTime())); }
        private double timeoutMillis(Duration preferred) {
            long remaining = Math.max(1, remaining().toMillis());
            return Math.max(1, Math.min(preferred.toMillis(), remaining));
        }
        private void requireTime() {
            if (System.nanoTime() >= endNanos) throw new TimeoutError("Durata totală a capturii a fost depășită.");
        }
    }
}
