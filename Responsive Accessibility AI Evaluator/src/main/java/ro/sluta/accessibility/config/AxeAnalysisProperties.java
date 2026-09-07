package ro.sluta.accessibility.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.axe")
public class AxeAnalysisProperties {
    private String profileVersion = "WCAG_22_AA_V1";
    private String adapterVersion = "4.13.0";
    private Duration timeout = Duration.ofSeconds(30);
    private List<String> tags = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");

    public String getProfileVersion() { return profileVersion; }
    public void setProfileVersion(String value) { profileVersion = value; }
    public String getAdapterVersion() { return adapterVersion; }
    public void setAdapterVersion(String value) { adapterVersion = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }
    public List<String> getTags() { return List.copyOf(tags); }
    public void setTags(List<String> value) { tags = List.copyOf(value); }
}
