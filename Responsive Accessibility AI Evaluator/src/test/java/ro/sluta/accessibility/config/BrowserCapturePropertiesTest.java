package ro.sluta.accessibility.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BrowserCapturePropertiesTest {
    @Test
    void defaultsMatchTheApprovedF2Limits() {
        BrowserCaptureProperties properties = new BrowserCaptureProperties();
        assertThat(properties.isHeadless()).isTrue();
        assertThat(properties.getLimits().getNavigationTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getLimits().getTotalCaptureTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(properties.getLimits().getInteractionStepTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getLimits().getMaxRedirects()).isEqualTo(5);
        assertThat(properties.getLimits().getMaxResources()).isEqualTo(250);
        assertThat(properties.getLimits().getMaxMainDocumentBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(properties.getLimits().getMaxInteractionSteps()).isEqualTo(20);
        assertThat(properties.getStabilization().getFontTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getStabilization().getFinalDelay()).isEqualTo(Duration.ofMillis(500));
    }
}
