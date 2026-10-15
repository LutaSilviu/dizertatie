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
import usv.dizertatie.service.BenchmarkRunnerService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
}
