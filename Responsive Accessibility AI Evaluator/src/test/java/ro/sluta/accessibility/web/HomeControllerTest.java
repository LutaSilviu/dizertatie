package ro.sluta.accessibility.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisExecution;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisResult;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisStatus;
import ro.sluta.accessibility.analysis.axe.RunAxeAnalysisUseCase;
import ro.sluta.accessibility.application.CapturePageSnapshotUseCase;
import ro.sluta.accessibility.browser.interaction.InteractionScenarioRegistry;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.SnapshotErrorCode;
import ro.sluta.accessibility.domain.SnapshotStatus;
import ro.sluta.accessibility.domain.Viewport;
import ro.sluta.accessibility.security.PublicUrlValidator;
import ro.sluta.accessibility.config.AiAnalysisProperties;
import ro.sluta.accessibility.normalization.FindingNormalizerV1;
import ro.sluta.accessibility.reporting.NormalizedReportAssembler;
import ro.sluta.accessibility.reporting.ComponentReport;
import ro.sluta.accessibility.reporting.ReportStatus;

@WebMvcTest(controllers = {HomeController.class, UrlValidationController.class, SnapshotController.class,
        AxeAnalysisController.class})
@Import({PublicUrlValidator.class, InteractionScenarioRegistry.class})
class HomeControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CapturePageSnapshotUseCase captureUseCase;
    @MockitoBean private RunAxeAnalysisUseCase axeUseCase;
    @MockitoBean private AiAnalysisProperties aiProperties;
    @MockitoBean private FindingNormalizerV1 findingNormalizer;
    @MockitoBean private NormalizedReportAssembler reportAssembler;

    @BeforeEach
    void aiCatalog() {
        when(aiProperties.models()).thenReturn(java.util.Map.of());
    }

    @Test
    void rendersThymeleafHomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Capturează starea paginii")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"url\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"viewport\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Capturează și rulează axe-core")));
    }

    @Test
    void showsValidationErrorWithoutStartingAnalysis() throws Exception {
        mockMvc.perform(post("/validate-url").param("url", "http://localhost:8080"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("destinație locală")));
    }

    @Test
    void presentsStructuredF2StatusWithoutAccessibilityReport() throws Exception {
        when(captureUseCase.capture(any())).thenReturn(new PageSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), URI.create("https://example.com"), null,
                Instant.now(), Viewport.DESKTOP, null, null, null, List.of(), List.of(),
                SnapshotStatus.BLOCKED, List.of("Destinație blocată"), SnapshotErrorCode.BLOCKED_ADDRESS,
                "Destinația nu este permisă.", "f2-v1", null, "none@1", List.of()));

        mockMvc.perform(post("/capture-snapshot")
                        .param("url", "https://example.com")
                        .param("viewport", "DESKTOP")
                        .param("interactionScenarioId", "none"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BLOCKED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BLOCKED_ADDRESS")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Raport de accesibilitate"))));
    }

    @Test
    void rendersTheSeparateTechnicalAxeReportAndCertificationWarning() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        PageSnapshot snapshot = new PageSnapshot(snapshotId, runId, URI.create("https://example.com"),
                URI.create("https://example.com"), Instant.now(), Viewport.DESKTOP, null, null, null,
                List.of(), List.of(), SnapshotStatus.SUCCESS, List.of(), null, null, "f2-v1",
                "a".repeat(64), "none@1", List.of());
        Instant now = Instant.now();
        AxeAnalysisResult axe = new AxeAnalysisResult(UUID.randomUUID(), runId, snapshotId, Viewport.DESKTOP,
                URI.create("https://example.com"), snapshot.contentHash(), "WCAG_22_AA_V1",
                List.of("wcag2a", "wcag2aa"), "4.13.0", "4.13.0", now, now, 12, 30_000,
                AxeAnalysisStatus.SUCCESS, List.of(), null, null, List.of(), List.of(), List.of(), List.of(),
                List.of());
        when(axeUseCase.analyze(any())).thenReturn(new AxeAnalysisExecution(snapshot, axe));

        mockMvc.perform(post("/analyze-axe")
                        .param("url", "https://example.com")
                        .param("viewport", "DESKTOP")
                        .param("interactionScenarioId", "none"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Raport tehnic axe-core")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("WCAG_22_AA_V1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "nu reprezintă certificare oficială WCAG")));
    }

    @Test
    void rendersNormalizedPartialReportWithAllClientSideFilters() throws Exception {
        UUID runId = UUID.randomUUID(); UUID snapshotId = UUID.randomUUID();
        PageSnapshot snapshot = new PageSnapshot(snapshotId, runId, URI.create("https://example.com"),
                URI.create("https://example.com"), Instant.now(), Viewport.DESKTOP, null, null, null,
                List.of(), List.of(), SnapshotStatus.SUCCESS, List.of(), null, null, "f2-v1",
                "a".repeat(64), "none@1", List.of());
        Instant now = Instant.now();
        AxeAnalysisResult axe = new AxeAnalysisResult(UUID.randomUUID(), runId, snapshotId, Viewport.DESKTOP,
                URI.create("https://example.com"), snapshot.contentHash(), "WCAG_22_AA_V1", List.of(),
                "4.13.0", "4.13.0", now, now, 10, 30_000, AxeAnalysisStatus.SUCCESS, List.of(),
                null, null, List.of(), List.of(), List.of(), List.of(), List.of());
        when(axeUseCase.analyze(any())).thenReturn(new AxeAnalysisExecution(snapshot, axe));
        var report = new NormalizedReportAssembler().assemble(List.of(snapshot), List.of(), List.of(
                new ComponentReport("BROWSER", ReportStatus.SUCCESS, 2, null, null),
                new ComponentReport("AI", ReportStatus.FAILED, 90, "AI_TIMEOUT", "timeout")),
                java.util.Map.of("method", "COMPARE", "model", "GPT5_NANO"), List.of("AI indisponibil"),
                0, 0, java.math.BigDecimal.ZERO);
        when(reportAssembler.forAxe(eq(snapshot), eq(axe), any())).thenReturn(report);

        mockMvc.perform(post("/analyze-axe").param("url", "https://example.com")
                        .param("viewport", "DESKTOP").param("interactionScenarioId", "none"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Raport normalizat")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rezultat parțial")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"filter-viewport\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"filter-method\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"filter-model\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"filter-source\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"filter-wcag\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"filter-severity\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"filter-status\"")));
    }
}
