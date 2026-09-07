package ro.sluta.accessibility.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.sluta.accessibility.domain.AnalysisMethod;
import ro.sluta.accessibility.domain.AnalysisStatus;
import ro.sluta.accessibility.export.ResearchExportService;
import ro.sluta.accessibility.persistence.*;

class HistoryExportControllerTest {
    private ResearchPersistenceService persistence;
    private ResearchExportService exports;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        persistence = mock(ResearchPersistenceService.class);
        exports = mock(ResearchExportService.class);
        mvc = MockMvcBuilders.standaloneSetup(new HistoryExportController(persistence, exports)).build();
    }

    @Test void historyMapsAllFiltersAndBoundsPagination() throws Exception {
        when(persistence.history(any())).thenReturn(new HistoryPage(List.of(), 0, 0, 100));
        mvc.perform(get("/api/v1/analyses/history")
                        .param("from", "2026-08-01T00:00:00Z").param("to", "2026-09-01T00:00:00Z")
                        .param("url", "example").param("method", "AI").param("model", "GPT5_NANO")
                        .param("status", "COMPLETED").param("page", "-2").param("size", "500")
                        .param("ascending", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        ArgumentCaptor<HistoryFilter> filter = ArgumentCaptor.forClass(HistoryFilter.class);
        verify(persistence).history(filter.capture());
        assertThat(filter.getValue()).satisfies(value -> {
            assertThat(value.page()).isZero(); assertThat(value.size()).isEqualTo(100);
            assertThat(value.method()).isEqualTo(AnalysisMethod.AI);
            assertThat(value.status()).isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(value.modelId()).isEqualTo("GPT5_NANO"); assertThat(value.ascending()).isTrue();
        });
    }

    @Test void repeatReturnsCreatedWithNewAnalysis() throws Exception {
        UUID source = UUID.randomUUID(), repeatedId = UUID.randomUUID(); Instant now = Instant.parse("2026-09-01T00:00:00Z");
        AnalysisRecord repeated = new AnalysisRecord(repeatedId, source, "https://example.com", "{}",
                AnalysisStatus.CREATED, 0, now, now, 0);
        when(persistence.repeat(org.mockito.ArgumentMatchers.eq(source), any())).thenReturn(repeated);
        mvc.perform(post("/api/v1/analyses/{id}/repeat", source)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysisId").value(repeatedId.toString()))
                .andExpect(jsonPath("$.parentAnalysisId").value(source.toString()));
    }

    @Test void exposesAllVersionedExportFormatsAndRejectsUnknownFormat() throws Exception {
        UUID id = UUID.randomUUID(); ResearchBundle bundle = mock(ResearchBundle.class);
        when(persistence.loadBundle(id)).thenReturn(bundle);
        when(exports.json(bundle)).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(exports.findingsCsv(bundle)).thenReturn("f\r\n".getBytes(StandardCharsets.UTF_8));
        when(exports.evaluationsCsv(bundle)).thenReturn("e\r\n".getBytes(StandardCharsets.UTF_8));
        when(exports.runsCsv(bundle)).thenReturn("r\r\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/analyses/{id}/export/json", id)).andExpect(status().isOk())
                .andExpect(content().json("{}"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analysis.json\""));
        for (String format : List.of("findings.csv", "evaluations.csv", "runs.csv")) {
            mvc.perform(get("/api/v1/analyses/{id}/export/{format}", id, format)).andExpect(status().isOk())
                    .andExpect(content().contentType("text/csv"));
        }
        mvc.perform(get("/api/v1/analyses/{id}/export/xml", id)).andExpect(status().isBadRequest());
    }
}
