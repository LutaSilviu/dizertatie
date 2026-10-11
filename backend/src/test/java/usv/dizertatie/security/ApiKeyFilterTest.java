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
        "spring.ai.openai.api-key=test-openai-key"
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
        mockMvc.perform(get("/health"))
                .andExpect(status().isNotFound()); // 404 but not 401 - filter skips /health
    }
}
