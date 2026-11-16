package usv.dizertatie.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.domain.ScanStatus;
import usv.dizertatie.export.CsvSerializer;
import usv.dizertatie.playwright.PageBlockedException;
import usv.dizertatie.playwright.PageCaptureException;
import usv.dizertatie.ai.AiAnalysisException;
import com.microsoft.playwright.TimeoutError;
import usv.dizertatie.service.ScanService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import usv.dizertatie.ai.AiModel;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "APP_API_KEY=test-key",
        "skipPlaywrightInstall=true",
        "spring.ai.openai.api-key=test-key"
})
@AutoConfigureMockMvc
class ScanControllerTest {

    private static final String API_KEY = "test-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private CsvSerializer csvSerializer;

    @Test
    void getScans_withValidApiKey_returns200AndJsonArray() throws Exception {
        ScanSummaryResponse item = new ScanSummaryResponse(
                1L, "https://example.com", "AXE", null,
                Instant.now(), 3L, ScanStatus.COMPLETED, null
        );
        ScanHistoryResponse history = new ScanHistoryResponse(List.of(item), 0, 20, 1L);

        when(scanService.findAll(0, 20)).thenReturn(history);

        mockMvc.perform(get("/scans")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getScans_responseItemsContainExpectedFields() throws Exception {
        ScanSummaryResponse item = new ScanSummaryResponse(
                42L, "https://example.com/page", "AXE", null,
                Instant.parse("2026-10-01T10:00:00Z"), 12L, ScanStatus.COMPLETED, null
        );
        ScanHistoryResponse history = new ScanHistoryResponse(List.of(item), 0, 20, 1L);

        when(scanService.findAll(0, 20)).thenReturn(history);

        mockMvc.perform(get("/scans")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.content[0].url").value("https://example.com/page"))
                .andExpect(jsonPath("$.content[0].method").value("AXE"))
                .andExpect(jsonPath("$.content[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$.content[0].severityScore").doesNotExist())
                .andExpect(jsonPath("$.content[0].findingCount").value(12));
    }

    @Test
    void getScans_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/scans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postScans_targetSiteBlocksScanner_returns502WithReadableError() throws Exception {
        when(scanService.runAxeScan("https://blocked.example"))
                .thenThrow(new PageBlockedException(
                        "Target site served a bot challenge instead of the page: https://blocked.example"));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://blocked.example", "method": "AXE" }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("bot challenge")));
    }

    @Test
    void postScans_axeMethod_withValidApiKey_returns200WithId() throws Exception {
        Scan scan = Scan.builder()
                .id(42L)
                .url("https://example.com")
                .method("AXE")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.runAxeScan("https://example.com")).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AXE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void postScans_completedAxeScan_returnsFindingsMetricsAndManualReviewEvidence() throws Exception {
        Scan scan = Scan.builder()
                .id(44L)
                .url("https://example.com/report")
                .method("AXE")
                .timestamp(LocalDateTime.now())
                .rawAxeIncomplete("""
                        [{"id":"color-contrast","impact":"serious","description":"Check contrast",
                          "help":"Review contrast","helpUrl":"https://example.com/help",
                          "nodes":[{"target":[".hero"],"failureSummary":"Review the image background"}]}]
                        """)
                .build();
        Finding firstFinding = Finding.builder()
                .id(41L)
                .scan(scan)
                .sourceMethod("AXE")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .ruleId("image-alt")
                .severity("serious")
                .elementSelector("img")
                .description("Missing alt text")
                .build();
        Finding secondFinding = Finding.builder()
                .id(42L)
                .scan(scan)
                .sourceMethod("AXE")
                .wcagCriterion("1.4.3")
                .wcagLevel("AA")
                .ruleId("color-contrast")
                .severity("moderate")
                .elementSelector(".button")
                .description("Insufficient contrast")
                .build();

        when(scanService.runAxeScan("https://example.com/report")).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of(firstFinding, secondFinding));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com/report", "method": "AXE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(44))
                .andExpect(jsonPath("$.findings.length()").value(2))
                .andExpect(jsonPath("$.findings[0].ruleId").value("image-alt"))
                .andExpect(jsonPath("$.affectedElementsByImpact.serious").value(1))
                .andExpect(jsonPath("$.affectedElementsByImpact.moderate").value(1))
                .andExpect(jsonPath("$.distinctRuleCount").value(2))
                .andExpect(jsonPath("$.wcagCriteriaWithFindings[0]").value("1.1.1"))
                .andExpect(jsonPath("$.wcagCriteriaWithFindings[1]").value("1.4.3"))
                .andExpect(jsonPath("$.manualReviewItems.length()").value(1))
                .andExpect(jsonPath("$.manualReviewItems[0].ruleId").value("color-contrast"))
                .andExpect(jsonPath("$.manualReviewItems[0].elements[0].target").value(".hero"));
    }

    @Test
    void postScans_blockedAttempt_returnsPersistedFailureInsteadOfCompletedEmptyScan() throws Exception {
        Scan scan = Scan.builder()
                .id(43L)
                .url("https://example.com/blocked")
                .method("AXE")
                .status(ScanStatus.BLOCKED)
                .failureReason("Target site blocked access with HTTP 403.")
                .timestamp(LocalDateTime.now())
                .build();
        when(scanService.runAxeScan("https://example.com/blocked")).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com/blocked", "method": "AXE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.failureReason").value("Target site blocked access with HTTP 403."))
                .andExpect(jsonPath("$.findings").isEmpty())
                .andExpect(jsonPath("$.distinctRuleCount").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void getScansById_withValidId_withValidApiKey_returns200() throws Exception {
        Scan scan = Scan.builder()
                .id(1L)
                .url("https://example.com")
                .method("AXE")
                .timestamp(LocalDateTime.now())
                .build();

        Finding finding = Finding.builder()
                .id(10L)
                .scan(scan)
                .sourceMethod("AXE")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("serious")
                .elementSelector("img")
                .description("Missing alt text")
                .build();

        when(scanService.findById(1L)).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of(finding));

        mockMvc.perform(get("/scans/1")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.findings").isArray())
                .andExpect(jsonPath("$.findings[0].wcagCriterion").value("1.1.1"));
    }

    @Test
    void getScansById_reportsAxeEvidenceWithoutCompositeScores() throws Exception {
        Scan scan = Scan.builder()
                .id(2L)
                .url("https://example.com")
                .method("AXE")
                .timestamp(LocalDateTime.now())
                .build();

        Finding finding = Finding.builder()
                .id(20L)
                .scan(scan)
                .sourceMethod("AXE")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("serious")
                .ruleId("image-alt")
                .elementSelector("img")
                .description("Missing alt text")
                .build();

        scan.setRawAxeIncomplete("""
                [{"id":"color-contrast","impact":"serious","description":"Check contrast",
                  "help":"Review contrast","helpUrl":"https://example.com/help",
                  "nodes":[{"target":[".hero"],"failureSummary":"Review the image background"}]}]
                """);

        when(scanService.findById(2L)).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of(finding));

        mockMvc.perform(get("/scans/2")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.severityScore").doesNotExist())
                .andExpect(jsonPath("$.conformanceScores").doesNotExist())
                .andExpect(jsonPath("$.affectedElementsByImpact.serious").value(1))
                .andExpect(jsonPath("$.distinctRuleCount").value(1))
                .andExpect(jsonPath("$.wcagCriteriaWithFindings[0]").value("1.1.1"))
                .andExpect(jsonPath("$.manualReviewItems[0].ruleId").value("color-contrast"))
                .andExpect(jsonPath("$.manualReviewItems[0].elements[0].target").value(".hero"));
    }

    @Test
    void getScansById_completedWithoutFindingsReturnsExplicitEmptyEvidence() throws Exception {
        Scan scan = Scan.builder()
                .id(3L)
                .url("https://example.com")
                .method("AXE")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.findById(3L)).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of());

        mockMvc.perform(get("/scans/3")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.findings").isEmpty())
                .andExpect(jsonPath("$.distinctRuleCount").value(0))
                .andExpect(jsonPath("$.wcagCriteriaWithFindings").isEmpty())
                .andExpect(jsonPath("$.manualReviewItems").isEmpty());
    }

    @Test
    void getScansById_failedAttemptReturnsStatusAndReasonWithoutCompletedScanEvidence() throws Exception {
        Scan scan = Scan.builder()
                .id(4L)
                .url("https://example.com/private")
                .method("AXE")
                .status(ScanStatus.BLOCKED)
                .failureReason("Target site served a bot challenge instead of the page.")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.findById(4L)).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of());

        mockMvc.perform(get("/scans/4").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.failureReason").value("Target site served a bot challenge instead of the page."))
                .andExpect(jsonPath("$.findings").isEmpty())
                .andExpect(jsonPath("$.affectedElementsByImpact").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.distinctRuleCount").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.severityScore").doesNotExist());
    }

    @Test
    void getScans_historyIncludesFailedStatusAndReasonWithZeroFindings() throws Exception {
        ScanSummaryResponse item = new ScanSummaryResponse(
                4L, "https://example.com/private", "AXE", null,
                Instant.parse("2026-10-01T10:00:00Z"), 0L,
                ScanStatus.RATE_LIMITED, "Target site responded with HTTP 429."
        );
        when(scanService.findAll(0, 20)).thenReturn(new ScanHistoryResponse(List.of(item), 0, 20, 1));

        mockMvc.perform(get("/scans").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.content[0].failureReason").value("Target site responded with HTTP 429."))
                .andExpect(jsonPath("$.content[0].findingCount").value(0));
    }

    @Test
    void getScansById_withUnknownId_withValidApiKey_returns404() throws Exception {
        when(scanService.findById(999L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Scan not found: 999"));

        mockMvc.perform(get("/scans/999")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void postScans_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AXE" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postScans_aiMethod_withoutModel_defaultsToGpt6Luna_returns200WithId() throws Exception {
        Scan scan = Scan.builder()
                .id(55L)
                .url("https://example.com")
                .method("AI")
                .modelId("gpt-6-luna")
                .promptVersion("v1")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.runAiScan(eq("https://example.com"), eq(AiModel.GPT6_LUNA))).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AI" }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.model").value("gpt-6-luna"));
    }

    @Test
    void postScans_combinedMethod_withoutModel_defaultsToGpt6Luna_returns200WithId() throws Exception {
        Scan scan = Scan.builder()
                .id(99L)
                .url("https://example.com")
                .method("COMBINED")
                .axeVersion("4.9.0")
                .modelId("gpt-6-luna")
                .promptVersion("v1")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.runCombinedScan(eq("https://example.com"), eq(AiModel.GPT6_LUNA))).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "COMBINED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.method").value("COMBINED"));
    }

    @Test
    void postScans_combinedMethod_withGPT4O_withValidApiKey_returns200WithId() throws Exception {
        Scan scan = Scan.builder()
                .id(101L)
                .url("https://example.com")
                .method("COMBINED")
                .axeVersion("4.9.0")
                .modelId("gpt-4o")
                .promptVersion("v1")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.runCombinedScan(eq("https://example.com"), eq(AiModel.GPT4O))).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "COMBINED", "model": "GPT4O" }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.method").value("COMBINED"));
    }

    @Test
    void postScans_combinedMethod_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "COMBINED" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postScans_aiMethod_withGPT4O_model_withValidApiKey_returns200() throws Exception {
        Scan scan = Scan.builder()
                .id(77L)
                .url("https://example.com")
                .method("AI")
                .modelId("gpt-4o")
                .promptVersion("v1")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.runAiScan(eq("https://example.com"), eq(AiModel.GPT4O))).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AI", "model": "GPT4O" }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(77));
    }

    @Test
    void exportCsv_withValidId_withValidApiKey_returns200AndCsvContentType() throws Exception {
        Scan scan = Scan.builder()
                .id(1L)
                .url("https://example.com")
                .method("AXE")
                .timestamp(LocalDateTime.now())
                .build();

        Finding finding = Finding.builder()
                .id(10L)
                .scan(scan)
                .sourceMethod("AXE")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("serious")
                .elementSelector("img")
                .description("Missing alt text")
                .build();

        String csvContent = "sourceMethod,wcagCriterion,wcagLevel,severity,elementSelector,description\n"
                + "AXE,1.1.1,A,serious,img,Missing alt text\n";

        when(scanService.findById(1L)).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of(finding));
        when(csvSerializer.serialize(scan, List.of(finding))).thenReturn(csvContent);

        mockMvc.perform(get("/scans/1/export.csv")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"scan-1.csv\""))
                .andExpect(content().string(containsString(
                        "sourceMethod,wcagCriterion,wcagLevel,severity,elementSelector,description")));
    }

    @Test
    void exportCsv_withUnknownId_withValidApiKey_returns404() throws Exception {
        when(scanService.findById(999L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Scan not found: 999"));

        mockMvc.perform(get("/scans/999/export.csv")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportCsv_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/scans/1/export.csv"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST response carries the persisted findings and their evidence summary ---------

    @Test
    void postScans_responseIncludesPersistedFindingsAndEvidence() throws Exception {
        Scan scan = Scan.builder().id(70L).url("https://example.com").method("AXE")
                .timestamp(LocalDateTime.now()).build();
        Finding finding = Finding.builder().id(1L).scan(scan).sourceMethod("AXE")
                .wcagCriterion("1.1.1").wcagLevel("A").severity("critical")
                .elementSelector("img").description("Missing alt text").build();
        when(scanService.runAxeScan("https://example.com")).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of(finding));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AXE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings.length()").value(1))
                .andExpect(jsonPath("$.findings[0].wcagCriterion").value("1.1.1"))
                .andExpect(jsonPath("$.affectedElementsByImpact.critical").value(1))
                .andExpect(jsonPath("$.findingsBySource.AXE").value(1))
                .andExpect(jsonPath("$.wcagCriteriaWithFindings[0]").value("1.1.1"));
    }

    @Test
    void postScans_urlWithoutScheme_isNormalisedToHttps() throws Exception {
        Scan scan = Scan.builder().id(71L).url("https://example.com").method("AXE")
                .timestamp(LocalDateTime.now()).build();
        when(scanService.runAxeScan("https://example.com")).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "  example.com ", "method": "AXE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(71));
    }

    @Test
    void postScans_invalidUrl_returns400WithReadableError() throws Exception {
        for (String url : new String[]{"", "ftp://example.com/file", "http://", "https://exa mple.com"}) {
            mockMvc.perform(post("/scans")
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"url\": \"" + url + "\", \"method\": \"AXE\" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isNotEmpty());
        }
        verifyNoInteractions(scanService);
    }

    @Test
    void postScans_unsupportedMethod_returns400WithReadableError() throws Exception {
        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "LIGHTHOUSE" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Unsupported method: LIGHTHOUSE")));
    }

    @Test
    void postScans_unsupportedModel_returns400WithReadableError() throws Exception {
        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AI", "model": "gpt-2" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Unsupported model: gpt-2")));
    }

    @Test
    void postScans_frontendModelIds_areAccepted() throws Exception {
        Scan scan = Scan.builder().id(72L).url("https://example.com").method("AI")
                .timestamp(LocalDateTime.now()).build();
        when(scanService.runAiScan(eq("https://example.com"), any(AiModel.class))).thenReturn(scan);

        for (String model : new String[]{"gpt-6-luna", "gpt-4o", "gpt-4o-mini"}) {
            mockMvc.perform(post("/scans")
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"url\": \"https://example.com\", \"method\": \"AI\", \"model\": \"" + model + "\" }"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void postScans_malformedJson_returns400WithReadableError() throws Exception {
        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void postScans_unreachableHost_returns502WithReadableError() throws Exception {
        when(scanService.runAxeScan("https://unreachable.invalid"))
                .thenThrow(new PageCaptureException(
                        "Could not load https://unreachable.invalid: net::ERR_NAME_NOT_RESOLVED"));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://unreachable.invalid", "method": "AXE" }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("ERR_NAME_NOT_RESOLVED")));
    }

    @Test
    void postScans_pageTimesOut_returns504() throws Exception {
        when(scanService.runAxeScan("https://slow.example"))
                .thenThrow(new PageCaptureException("Could not load https://slow.example: timed out",
                        new TimeoutError("Timeout 30000ms exceeded.")));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://slow.example", "method": "AXE" }
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value(containsString("timed out")));
    }

    @Test
    void postScans_openAiRejectsKey_returns503WithReadableError() throws Exception {
        when(scanService.runAiScan(eq("https://example.com"), any(AiModel.class)))
                .thenThrow(new AiAnalysisException("AI analysis failed for model gpt-6-luna",
                        new RuntimeException("401: Incorrect API key provided: sk-place***-key. You can find your API key at https://platform.openai.com/account/api-keys.")));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AI" }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(
                        "OpenAI rejected the API key. Set a valid OPENAI_API_KEY in .env and restart the backend."));
    }

    @Test
    void postScans_otherAiFailure_returns502WithReadableError() throws Exception {
        when(scanService.runCombinedScan(eq("https://example.com"), any(AiModel.class)))
                .thenThrow(new AiAnalysisException("AI analysis failed for model gpt-4o",
                        new RuntimeException("HTTP 500 server error")));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "COMBINED", "model": "gpt-4o" }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("AI analysis failed")));
    }

    @Test
    void getScansById_withUnknownId_returnsJsonErrorMessage() throws Exception {
        when(scanService.findById(998L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Scan not found: 998"));

        mockMvc.perform(get("/scans/998").header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Scan not found: 998"));
    }

    @Test
    void exportCsv_withUnknownId_returnsJsonErrorMessage() throws Exception {
        when(scanService.findById(997L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Scan not found: 997"));

        mockMvc.perform(get("/scans/997/export.csv").header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Scan not found: 997"));
    }

    @Test
    void getScansById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/scans/abc").header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("id")));
    }

    @Test
    void getScans_invalidPaging_returns400() throws Exception {
        mockMvc.perform(get("/scans?page=-1").header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/scans?size=0").header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/scans?size=1000").header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getScans_includesTotalPagesAndNumber() throws Exception {
        when(scanService.findAll(1, 20)).thenReturn(new ScanHistoryResponse(List.of(), 1, 20, 45L));

        mockMvc.perform(get("/scans?page=1&size=20").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void aiStatus_withPlaceholderKey_reportsMissingKey() throws Exception {
        mockMvc.perform(get("/ai/status").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISSING_KEY"))
                .andExpect(jsonPath("$.message").value(containsString("OPENAI_API_KEY")));
    }

    @Test
    void aiStatus_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/ai/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void postScans_browserErrorDump_isReportedInPlainEnglish() throws Exception {
        when(scanService.runAxeScan("https://nope.invalid")).thenThrow(new com.microsoft.playwright.PlaywrightException(
                "Error {\n  message='net::ERR_NAME_NOT_RESOLVED at https://nope.invalid/\nCall log:\n'\n  name='Error'\n}"));

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://nope.invalid", "method": "AXE" }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("domain name could not be resolved")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(containsString("Error {"))));
    }

    @Test
    void postScans_aiWithMissingKey_returns503WithTheSameMessageAsAiStatus() throws Exception {
        when(scanService.runAiScan(eq("https://example.com"), any(AiModel.class)))
                .thenThrow(new usv.dizertatie.ai.AiKeyMissingException());

        String statusMessage = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get("/ai/status").header("X-API-Key", API_KEY))
                        .andReturn().getResponse().getContentAsString(), "$.message");

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AI" }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(statusMessage));
    }

    // --- #43: the report can tell which engine and model produced a scan ------------------

    @Test
    void getScan_axeScan_exposesEngineAndBrowserVersion() throws Exception {
        Scan scan = Scan.builder().id(80L).url("https://example.com").method("AXE")
                .axeVersion("4.14.0").browserVersion("chromium/130.0")
                .timestamp(LocalDateTime.now()).build();
        when(scanService.findById(80L)).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of());

        mockMvc.perform(get("/scans/80").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.axeVersion").value("4.14.0"))
                .andExpect(jsonPath("$.browserVersion").value("chromium/130.0"));
    }

    // --- #42: the evidence summary covers the same findings as the table ------------------

    @Test
    void getScan_aiScan_countsAiFindingsInTheEvidenceSummary() throws Exception {
        Scan scan = Scan.builder().id(81L).url("https://example.com").method("AI")
                .modelId("gpt-4o-mini").timestamp(LocalDateTime.now()).build();
        when(scanService.findById(81L)).thenReturn(scan);
        when(scanService.findingsForScan(scan)).thenReturn(List.of(
                Finding.builder().sourceMethod("AI").wcagCriterion("1.1.1").severity("critical")
                        .elementSelector("img").build(),
                Finding.builder().sourceMethod("AI").wcagCriterion("2.4.4").severity("serious")
                        .elementSelector("a").build()));

        mockMvc.perform(get("/scans/81").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.findings.length()").value(2))
                .andExpect(jsonPath("$.affectedElementsByImpact.critical").value(1))
                .andExpect(jsonPath("$.affectedElementsByImpact.serious").value(1))
                .andExpect(jsonPath("$.findingsBySource.AI").value(2))
                .andExpect(jsonPath("$.distinctRuleCount").value(0));
    }
}
