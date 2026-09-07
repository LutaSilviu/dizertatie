package ro.sluta.accessibility.analysis.axe;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.microsoft.playwright.Page;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class DequePlaywrightAxeEngine implements AxeEngine {
    @Override
    public AxeResults analyze(Page page, List<String> tags) {
        return new AxeBuilder(page).withTags(tags).analyze();
    }

    @Override
    public String adapterVersion() {
        String version = AxeBuilder.class.getPackage().getImplementationVersion();
        if (version != null) return version;
        try (InputStream stream = AxeBuilder.class.getClassLoader().getResourceAsStream(
                "META-INF/maven/com.deque.html.axe-core/playwright/pom.properties")) {
            if (stream == null) return "unknown";
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("version", "unknown");
        } catch (IOException exception) {
            return "unknown";
        }
    }
}
