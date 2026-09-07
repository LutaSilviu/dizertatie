package ro.sluta.accessibility.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.browser")
public class BrowserCaptureProperties {
    private boolean headless = true;
    private String extractorVersion = "f2-v1";
    private final Limits limits = new Limits();
    private final Stabilization stabilization = new Stabilization();

    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }
    public String getExtractorVersion() { return extractorVersion; }
    public void setExtractorVersion(String extractorVersion) { this.extractorVersion = extractorVersion; }
    public Limits getLimits() { return limits; }
    public Stabilization getStabilization() { return stabilization; }

    public static class Limits {
        private Duration navigationTimeout = Duration.ofSeconds(30);
        private Duration totalCaptureTimeout = Duration.ofSeconds(45);
        private Duration interactionStepTimeout = Duration.ofSeconds(5);
        private int maxRedirects = 5;
        private int maxResources = 250;
        private long maxMainDocumentBytes = 10L * 1024 * 1024;
        private int maxInteractionSteps = 20;

        public Duration getNavigationTimeout() { return navigationTimeout; }
        public void setNavigationTimeout(Duration value) { navigationTimeout = value; }
        public Duration getTotalCaptureTimeout() { return totalCaptureTimeout; }
        public void setTotalCaptureTimeout(Duration value) { totalCaptureTimeout = value; }
        public Duration getInteractionStepTimeout() { return interactionStepTimeout; }
        public void setInteractionStepTimeout(Duration value) { interactionStepTimeout = value; }
        public int getMaxRedirects() { return maxRedirects; }
        public void setMaxRedirects(int value) { maxRedirects = value; }
        public int getMaxResources() { return maxResources; }
        public void setMaxResources(int value) { maxResources = value; }
        public long getMaxMainDocumentBytes() { return maxMainDocumentBytes; }
        public void setMaxMainDocumentBytes(long value) { maxMainDocumentBytes = value; }
        public int getMaxInteractionSteps() { return maxInteractionSteps; }
        public void setMaxInteractionSteps(int value) { maxInteractionSteps = value; }
    }

    public static class Stabilization {
        private Duration fontTimeout = Duration.ofSeconds(2);
        private Duration finalDelay = Duration.ofMillis(500);

        public Duration getFontTimeout() { return fontTimeout; }
        public void setFontTimeout(Duration value) { fontTimeout = value; }
        public Duration getFinalDelay() { return finalDelay; }
        public void setFinalDelay(Duration value) { finalDelay = value; }
    }
}
