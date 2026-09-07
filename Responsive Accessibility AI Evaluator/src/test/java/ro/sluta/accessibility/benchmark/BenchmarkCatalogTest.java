package ro.sluta.accessibility.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ro.sluta.accessibility.domain.GroundTruthIssue;
import ro.sluta.accessibility.domain.Viewport;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BenchmarkCatalogTest {
    @Autowired BenchmarkCatalog catalog;
    @Autowired MockMvc mvc;

    @Test void generatesExactlyTwentyFourFixturesAndFortyEightCasesWithApprovedViewports() {
        assertThat(catalog.scenarios()).hasSize(12);
        assertThat(catalog.fixtures()).hasSize(24).extracting(BenchmarkFixture::fixtureHash)
                .allSatisfy(hash -> assertThat(hash).matches("[0-9a-f]{64}"));
        assertThat(catalog.cases()).hasSize(48);
        assertThat(catalog.cases().stream().filter(value -> value.viewport() == Viewport.DESKTOP)).hasSize(24);
        assertThat(catalog.cases().stream().filter(value -> value.viewport() == Viewport.MOBILE)).hasSize(22);
        assertThat(catalog.cases().stream().filter(value -> value.viewport() == Viewport.REFLOW_320)).hasSize(2);
        assertThat(catalog.cases().stream().filter(value -> value.fixture().scenario().scenarioId().equals("S12")))
                .extracting(BenchmarkCase::viewport).containsExactlyInAnyOrder(Viewport.DESKTOP, Viewport.REFLOW_320,
                        Viewport.DESKTOP, Viewport.REFLOW_320);
        assertThat(catalog.cases().stream().filter(value -> value.fixture().scenario().responsiveOnly()
                && value.viewport() == Viewport.DESKTOP && value.fixture().variant() == BenchmarkVariant.BAD))
                .allSatisfy(value -> assertThat(value.groundTruth().expected())
                        .isEqualTo(GroundTruthIssue.ExpectedPresence.ABSENT));
    }

    @Test void exposesAllStableRoutesWithoutExternalResources() throws Exception {
        Set<String> bodies = new HashSet<>();
        for (BenchmarkFixture fixture : catalog.fixtures()) {
            String body = mvc.perform(get("/benchmark/{scenario}/{variant}", fixture.scenario().scenarioId(), fixture.variant()))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).contains(fixture.fixtureId(), fixture.fixtureHash()).doesNotContain("src=\"http", "href=\"http");
            bodies.add(fixture.fixtureId());
        }
        assertThat(bodies).hasSize(24);
        mvc.perform(get("/api/v1/benchmark/manifest")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures").value(24)).andExpect(jsonPath("$.cases").value(48))
                .andExpect(jsonPath("$.caseManifest.length()").value(48));
    }
}
