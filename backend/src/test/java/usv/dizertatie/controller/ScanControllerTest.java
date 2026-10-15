package usv.dizertatie.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.repository.ScanRepository;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scan_test_db;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "APP_API_KEY=test-key",
        "spring.ai.openai.api-key=test-openai-key",
        "skipPlaywrightInstall=true"
})
@AutoConfigureMockMvc
class ScanControllerTest {

    private static final String API_KEY = "test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScanRepository scanRepository;

    @BeforeEach
    void setUp() {
        scanRepository.deleteAll();

        scanRepository.save(Scan.builder()
                .url("https://example.com/page1")
                .method("AXE")
                .timestamp(LocalDateTime.now().minusHours(2))
                .build());

        scanRepository.save(Scan.builder()
                .url("https://example.com/page2")
                .method("AXE")
                .timestamp(LocalDateTime.now().minusHours(1))
                .build());
    }

    @Test
    void getScans_withValidApiKey_returns200AndJsonArray() throws Exception {
        mockMvc.perform(get("/scans")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getScans_withValidApiKey_returnsNewestFirst() throws Exception {
        mockMvc.perform(get("/scans")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].url").value("https://example.com/page2"))
                .andExpect(jsonPath("$.content[1].url").value("https://example.com/page1"));
    }

    @Test
    void getScans_withPageParams_returnsPaginatedResults() throws Exception {
        mockMvc.perform(get("/scans")
                        .header("X-API-Key", API_KEY)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getScans_responseItemsContainExpectedFields() throws Exception {
        mockMvc.perform(get("/scans")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].url").isString())
                .andExpect(jsonPath("$.content[0].method").value("AXE"))
                .andExpect(jsonPath("$.content[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$.content[0].severityScore").isNumber())
                .andExpect(jsonPath("$.content[0].findingCount").isNumber());
    }

    @Test
    void getScans_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/scans"))
                .andExpect(status().isUnauthorized());
    }
}
