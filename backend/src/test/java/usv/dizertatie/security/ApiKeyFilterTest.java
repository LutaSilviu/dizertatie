package usv.dizertatie.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.api-key=secret-key",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.base-url=http://localhost"
})
class ApiKeyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_withoutKey_returns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void health_withWrongKey_returns200() throws Exception {
        mockMvc.perform(get("/health")
                        .header("X-API-Key", "wrong-key"))
                .andExpect(status().isOk());
    }

    @Test
    void scans_withoutKey_returns401() throws Exception {
        mockMvc.perform(get("/scans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scans_withCorrectKey_isNot401() throws Exception {
        int statusCode = mockMvc.perform(get("/scans")
                        .header("X-API-Key", "secret-key"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(statusCode).isNotEqualTo(401);
    }
}
