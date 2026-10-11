package usv.dizertatie.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "APP_API_KEY=test-secret-key",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class LabelControllerTest {

    private static final String API_KEY = "test-secret-key";
    private static final String W3C_BAD_URL = "https://www.w3.org/WAI/demos/bad/before/home.html";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GroundTruthLabelRepository repository;

    @Test
    void getAllLabels_withValidApiKey_returns200AndNonEmptyList() throws Exception {
        mockMvc.perform(get("/labels")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void getAllLabels_filteredByUrl_returnsOnlyLabelsForThatUrl() throws Exception {
        mockMvc.perform(get("/labels")
                        .header("X-API-Key", API_KEY)
                        .param("url", W3C_BAD_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].url").value(W3C_BAD_URL));
    }

    @Test
    void getAllLabels_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/labels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLabel_withValidApiKey_returns201AndCreatedLabel() throws Exception {
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
        // First create a label to delete
        String body = """
                {
                    "url": "https://example.com/to-delete",
                    "wcagCriterion": "2.4.1",
                    "wcagLevel": "A",
                    "severity": "MINOR",
                    "elementSelector": "nav",
                    "annotator": "tester",
                    "verifiedBy": null,
                    "confidence": 0.8
                }
                """;

        MvcResult result = mockMvc.perform(post("/labels")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        // Extract the id from the response using simple string parsing
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Long id = mapper.readTree(responseJson).get("id").asLong();

        mockMvc.perform(delete("/labels/" + id)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteLabel_nonExisting_returns404() throws Exception {
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
