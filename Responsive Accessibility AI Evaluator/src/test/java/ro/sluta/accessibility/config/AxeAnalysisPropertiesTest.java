package ro.sluta.accessibility.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AxeAnalysisPropertiesTest {
    @Test
    void freezesTheApprovedWcagProfileTagsVersionAndTimeout() {
        AxeAnalysisProperties properties = new AxeAnalysisProperties();

        assertThat(properties.getProfileVersion()).isEqualTo("WCAG_22_AA_V1");
        assertThat(properties.getAdapterVersion()).isEqualTo("4.13.0");
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getTags()).containsExactly(
                "wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
    }
}
