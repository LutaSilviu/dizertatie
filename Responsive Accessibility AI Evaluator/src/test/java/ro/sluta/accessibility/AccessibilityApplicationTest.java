package ro.sluta.accessibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AccessibilityApplicationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void applicationContextLoads() {
        assertThat(true).isTrue();
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void modelsEndpointReturnsOnlyActiveConfiguredModels() throws Exception {
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].providerModel").value("gpt-4.1-nano"))
                .andExpect(jsonPath("$[1].providerModel").value("gpt-5-nano"));
    }

    @Test
    void estimateEndpointPublishesMarginAndEffectivePriceDateBeforeCalls() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/estimate")
                        .param("modelId", "GPT5_NANO")
                        .param("inputCharacters", "1000")
                        .param("plannedCalls", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedCalls").value(3))
                .andExpect(jsonPath("$.includesSafetyMargin").value(true))
                .andExpect(jsonPath("$.pricesEffectiveFrom").value("2026-09-01"));
    }
}
