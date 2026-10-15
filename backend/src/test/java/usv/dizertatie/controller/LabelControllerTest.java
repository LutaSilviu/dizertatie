package usv.dizertatie.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import usv.dizertatie.service.LabelService;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "APP_API_KEY=test-key",
        "spring.ai.openai.api-key=test-key"
})
@AutoConfigureMockMvc
class LabelControllerTest {

    private static final String API_KEY = "test-key";
    private static final String EXAMPLE_URL = "https://example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LabelService labelService;

    @Test
    void getAllLabels_withValidApiKey_returns200AndJsonArray() throws Exception {
        LabelResponse label = new LabelResponse(1L, EXAMPLE_URL, "1.1.1", "A",
                "CRITICAL", "img", "annotator1", null, 0.9);
        when(labelService.findAll()).thenReturn(List.of(label));

        mockMvc.perform(get("/labels")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].url").value(EXAMPLE_URL));
    }

    @Test
    void getAllLabels_filteredByUrl_returnsOnlyLabelsForThatUrl() throws Exception {
        LabelResponse label = new LabelResponse(2L, EXAMPLE_URL, "2.4.1", "A",
                "SERIOUS", "nav", "annotator2", "reviewer1", 0.85);
        when(labelService.findByUrl(EXAMPLE_URL)).thenReturn(List.of(label));

        mockMvc.perform(get("/labels")
                        .header("X-API-Key", API_KEY)
                        .param("url", EXAMPLE_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].url").value(EXAMPLE_URL));
    }

    @Test
    void getAllLabels_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/labels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLabel_withValidApiKey_returns201AndCreatedLabel() throws Exception {
        LabelResponse created = new LabelResponse(3L, "https://example.com/page",
                "1.1.1", "A", "CRITICAL", "img", "tester", null, 0.9);
        when(labelService.create(any())).thenReturn(created);

        String body = """
                {
                    "url": "https://example.com/page",
                    "wcagCriterion": "1.1.1",
                    "wcagLevel": "A",
                    "severity": "CRITICAL",
                    "elementSelector": "img",
                    "annotator": "tester",
                    "verifiedBy": null,
                    "confidence": 0.9
                }
                """;

        mockMvc.perform(post("/labels")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.url").value("https://example.com/page"))
                .andExpect(jsonPath("$.wcagCriterion").value("1.1.1"))
                .andExpect(jsonPath("$.annotator").value("tester"));
    }

    @Test
    void createLabel_withoutApiKey_returns401() throws Exception {
        String body = """
                {
                    "url": "https://example.com/page",
                    "wcagCriterion": "1.1.1",
                    "wcagLevel": "A",
                    "severity": "CRITICAL",
                    "elementSelector": "img",
                    "annotator": "tester",
                    "verifiedBy": null,
                    "confidence": 0.9
                }
                """;

        mockMvc.perform(post("/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteLabel_existing_returns204() throws Exception {
        doNothing().when(labelService).delete(1L);

        mockMvc.perform(delete("/labels/1")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteLabel_nonExisting_returns404() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Label not found: 999999"))
                .when(labelService).delete(999999L);

        mockMvc.perform(delete("/labels/999999")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteLabel_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(delete("/labels/1"))
                .andExpect(status().isUnauthorized());
    }
}
