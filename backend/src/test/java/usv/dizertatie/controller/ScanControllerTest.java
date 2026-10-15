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
import usv.dizertatie.service.ScanService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import usv.dizertatie.ai.AiModel;

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

    @Test
    void getScans_withValidApiKey_returns200AndJsonArray() throws Exception {
        ScanSummaryResponse item = new ScanSummaryResponse(
                1L, "https://example.com", "AXE", null,
                Instant.now(), 85.0, 3L
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
                Instant.parse("2026-10-01T10:00:00Z"), 76.3, 12L
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
                .andExpect(jsonPath("$.content[0].severityScore").value(76.3))
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
                                { "url": "https://example.com", "method": "AI" }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(55));
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
}
