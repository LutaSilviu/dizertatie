package ro.sluta.accessibility.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExperimentControllerTest {
    @Autowired MockMvc mvc;

    @Test void dashboardRendersTheFrozenMasterCounts() throws Exception {
        mvc.perform(get("/experiment")).andExpect(status().isOk()).andExpect(view().name("experiment"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("576")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Browser pornit: nu")));
    }

    @Test void dryRunApiHasExactCountsAndNoExternalSideEffects() throws Exception {
        mvc.perform(post("/api/v1/experiments/dry-run").contentType(MediaType.APPLICATION_JSON).content("""
                {"randomSeed":20260901,"aiConcurrency":1,"hardBudgetUsd":10.00,
                 "maxCostPerAiCallUsd":0.01,"pilotScenarioIds":["S01","S04"]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.plannedCases").value(48))
                .andExpect(jsonPath("$.plan.plannedAxeRuns").value(48))
                .andExpect(jsonPath("$.plan.plannedAiTextRuns").value(288))
                .andExpect(jsonPath("$.plan.plannedAiMultimodalRuns").value(288))
                .andExpect(jsonPath("$.plan.plannedAiCalls").value(576))
                .andExpect(jsonPath("$.plan.plannedHybridResults").value(288))
                .andExpect(jsonPath("$.browserStarted").value(false)).andExpect(jsonPath("$.apiCalled").value(false));
    }

    @Test void finalPlanRejectsMissingPaidConfirmationBeforeAnyCall() throws Exception {
        mvc.perform(post("/api/v1/experiments/plans/FINAL").contentType(MediaType.APPLICATION_JSON).content("""
                {"randomSeed":20260901,"aiConcurrency":1,"hardBudgetUsd":10.00,
                 "maxCostPerAiCallUsd":0.01,"pilotScenarioIds":["S01","S04"]}
                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("confirmare plătită")));
    }
}
