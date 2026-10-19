package usv.dizertatie.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "APP_API_KEY=test-secret-key",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ApiKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/some-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithWrongApiKey_returns401() throws Exception {
        mockMvc.perform(get("/some-endpoint")
                        .header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithCorrectApiKey_passesThrough() throws Exception {
        mockMvc.perform(get("/some-endpoint")
                        .header("X-API-Key", "test-secret-key"))
                .andExpect(status().isNotFound()); // 404 because endpoint doesn't exist, but filter passed
    }

    @Test
    void healthEndpoint_doesNotRequireApiKey() throws Exception {
        // The filter skips /health, so the request reaches HealthController: any
        // status but 401 proves the key was not required.
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
