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
import usv.dizertatie.service.ScanService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import usv.dizertatie.ai.AiModel;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
    void postScans_aiMethod_withValidApiKey_returns200WithId() throws Exception {
        Scan scan = Scan.builder()
                .id(55L)
                .url("https://example.com")
                .method("AI")
                .modelId("gpt-4o-mini")
                .promptVersion("v1")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.runAiScan(eq("https://example.com"), eq(AiModel.GPT4O_MINI))).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "AI", "model": "gpt-4o-mini" }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(55));
    }

    @Test
    void postScans_combinedMethod_withValidApiKey_returns200WithId() throws Exception {
        Scan scan = Scan.builder()
                .id(99L)
                .url("https://example.com")
                .method("COMBINED")
                .axeVersion("4.9.0")
                .modelId("gpt-4o-mini")
                .promptVersion("v1")
                .timestamp(LocalDateTime.now())
                .build();

        when(scanService.runCombinedScan(eq("https://example.com"), eq(AiModel.GPT4O_MINI))).thenReturn(scan);

        mockMvc.perform(post("/scans")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://example.com", "method": "COMBINED", "model": "gpt-4o-mini" }
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
}
