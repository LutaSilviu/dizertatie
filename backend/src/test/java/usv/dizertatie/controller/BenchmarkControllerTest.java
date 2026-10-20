package usv.dizertatie.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.export.BenchmarkCsvExporter;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.service.BenchmarkMetrics;
import usv.dizertatie.service.BenchmarkRunnerService;
import usv.dizertatie.service.MetricsService;
import usv.dizertatie.service.MethodMetrics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
class BenchmarkControllerTest {

    private static final String API_KEY = "test-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BenchmarkRunnerService benchmarkRunnerService;

    @MockitoBean
    private BenchmarkPageRepository benchmarkPageRepository;

    @MockitoBean
    private BenchmarkCsvExporter benchmarkCsvExporter;

    @MockitoBean
    private MetricsService metricsService;

    @Test
    void postBenchmarkRuns_withValidApiKey_returns202WithRunId() throws Exception {
        BenchmarkRun run = BenchmarkRun.builder()
                .id(1L)
                .name("Test Run")
                .status("PENDING")
                .createdAt(LocalDateTime.of(2026, 10, 15, 10, 0))
                .totalPages(2)
                .pagesDone(0)
                .build();

        when(benchmarkRunnerService.createRun(anyString(), anyList(), anyBoolean(), any(AiModel.class)))
                .thenReturn(run);

        mockMvc.perform(post("/benchmark-runs")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test Run",
                                  "urls": ["https://example.com", "https://example.org"],
                                  "useBuiltinSet": false,
                                  "model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.runId").value(1));
    }

    @Test
    void getBenchmarkRunStatus_withValidApiKey_returns200WithStatusFields() throws Exception {
        BenchmarkRun run = BenchmarkRun.builder()
                .id(1L)
                .name("My Run")
                .status("RUNNING")
                .createdAt(LocalDateTime.of(2026, 10, 15, 10, 0))
                .totalPages(20)
                .pagesDone(3)
                .build();

        when(benchmarkRunnerService.findById(1L)).thenReturn(Optional.of(run));

        mockMvc.perform(get("/benchmark-runs/1/status")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("My Run"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.pagesDone").value(3))
                .andExpect(jsonPath("$.totalPages").value(20))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void getBenchmarkRunStatus_withUnknownId_returns404() throws Exception {
        when(benchmarkRunnerService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/benchmark-runs/999/status")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void listBenchmarkRuns_withValidApiKey_returns200WithArray() throws Exception {
        BenchmarkRun run1 = BenchmarkRun.builder()
                .id(2L)
                .name("Run 2")
                .status("DONE")
                .createdAt(LocalDateTime.of(2026, 10, 15, 11, 0))
                .totalPages(5)
                .pagesDone(5)
                .build();

        BenchmarkRun run2 = BenchmarkRun.builder()
                .id(1L)
                .name("Run 1")
                .status("DONE")
                .createdAt(LocalDateTime.of(2026, 10, 14, 11, 0))
                .totalPages(3)
                .pagesDone(3)
                .build();

        when(benchmarkRunnerService.findAllNewestFirst()).thenReturn(List.of(run1, run2));

        mockMvc.perform(get("/benchmark-runs")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(1));
    }

    @Test
    void postBenchmarkRuns_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/benchmark-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Test", "urls": [], "useBuiltinSet": false }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBenchmarkRunStatus_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/benchmark-runs/1/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listBenchmarkRuns_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/benchmark-runs"))
                .andExpect(status().isUnauthorized());
    }

    // ---- export.csv tests ----

    @Test
    void exportCsv_withValidApiKey_returns200WithCsvContentTypeAndDispositionHeader() throws Exception {
        BenchmarkRun run = BenchmarkRun.builder()
                .id(5L)
                .name("Export Run")
                .status("COMPLETE")
                .createdAt(LocalDateTime.of(2026, 10, 15, 12, 0))
                .totalPages(1)
                .pagesDone(1)
                .build();

        when(benchmarkRunnerService.findById(5L)).thenReturn(Optional.of(run));
        when(benchmarkCsvExporter.export(5L)).thenReturn("benchmark CSV content");

        mockMvc.perform(get("/benchmark-runs/5/export.csv")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("benchmark-5.csv")))
                .andExpect(content().string("benchmark CSV content"));
    }

    @Test
    void exportCsv_withFailedRun_returnsHeaderOnlyCsv() throws Exception {
        BenchmarkRun run = BenchmarkRun.builder()
                .id(5L)
                .name("Failed Run")
                .status("FAILED")
                .createdAt(LocalDateTime.of(2026, 10, 15, 12, 0))
                .totalPages(1)
                .pagesDone(1)
                .build();
        String headerOnlyCsv = "run_id,page_url,method,wcag_criterion,wcag_level,severity,element_selector,description,ground_truth_match";

        when(benchmarkRunnerService.findById(5L)).thenReturn(Optional.of(run));
        when(benchmarkCsvExporter.export(5L)).thenReturn(headerOnlyCsv);

        mockMvc.perform(get("/benchmark-runs/5/export.csv")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().string(headerOnlyCsv));
    }

    @Test
    void exportCsv_withUnknownId_returns404() throws Exception {
        when(benchmarkRunnerService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/benchmark-runs/999/export.csv")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportCsv_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/benchmark-runs/5/export.csv"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /benchmark-runs/{id}/metrics ----

    @Test
    void getBenchmarkRunMetrics_withCompleteRun_returns200WithMethodsStructure() throws Exception {
        BenchmarkRun run = BenchmarkRun.builder()
                .id(1L)
                .name("Metrics Run")
                .status("COMPLETE")
                .createdAt(LocalDateTime.of(2026, 10, 15, 10, 0))
                .totalPages(3)
                .pagesDone(3)
                .build();

        BenchmarkMetrics metrics = new BenchmarkMetrics(
                1L,
                Map.of(
                        "AXE",      new MethodMetrics(0.82, 0.74, 0.78),
                        "AI",       new MethodMetrics(0.71, 0.88, 0.79),
                        "COMBINED", new MethodMetrics(0.68, 0.91, 0.78)
                ),
                Map.of(
                        "Perceivable", Map.of(
                                "AXE",      new MethodMetrics(0.9, 0.8, 0.85),
                                "AI",       new MethodMetrics(0.75, 0.85, 0.80),
                                "COMBINED", new MethodMetrics(0.70, 0.90, 0.79)
                        )
                ),
                List.of(),
                List.of(),
                0.0031,
                Map.of("AXE", 820.0, "AI", 4300.0, "COMBINED", 5100.0)
        );

        when(benchmarkRunnerService.findById(1L)).thenReturn(Optional.of(run));
        when(metricsService.compute(run)).thenReturn(metrics);

        mockMvc.perform(get("/benchmark-runs/1/metrics")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.runId").value(1))
                .andExpect(jsonPath("$.methods.AXE.precision").value(0.82))
                .andExpect(jsonPath("$.methods.AXE.recall").value(0.74))
                .andExpect(jsonPath("$.methods.AXE.f1").value(0.78))
                .andExpect(jsonPath("$.methods.AI.precision").value(0.71))
                .andExpect(jsonPath("$.methods.COMBINED.recall").value(0.91))
                .andExpect(jsonPath("$.byCategory.Perceivable.AXE.precision").value(0.9))
                .andExpect(jsonPath("$.avgCostPerPageUsd").value(0.0031))
                .andExpect(jsonPath("$.avgLatencyMs.AXE").value(820.0))
                .andExpect(jsonPath("$.avgLatencyMs.AI").value(4300.0))
                .andExpect(jsonPath("$.avgLatencyMs.COMBINED").value(5100.0));
    }

    @Test
    void getBenchmarkRunMetrics_withRunningRun_returns409() throws Exception {
        BenchmarkRun run = BenchmarkRun.builder()
                .id(2L)
                .name("In Progress")
                .status("RUNNING")
                .createdAt(LocalDateTime.of(2026, 10, 15, 10, 0))
                .totalPages(5)
                .pagesDone(2)
                .build();

        when(benchmarkRunnerService.findById(2L)).thenReturn(Optional.of(run));

        mockMvc.perform(get("/benchmark-runs/2/metrics")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isConflict());
    }

    @Test
    void getBenchmarkRunMetrics_withUnknownId_returns404() throws Exception {
        when(benchmarkRunnerService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/benchmark-runs/999/metrics")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBenchmarkRunMetrics_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/benchmark-runs/1/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
