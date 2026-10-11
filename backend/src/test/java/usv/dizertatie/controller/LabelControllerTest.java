package usv.dizertatie.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
