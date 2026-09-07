package ro.sluta.accessibility.analysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.sluta.accessibility.browser.ArtifactContentReader;

class PageContextBuilderV1Test {
    @TempDir Path root;

    @Test void buildsDeterministicSanitizedVersionedContextWithoutAxe() {
        var snapshot = AiTestFixtures.snapshot(root, UUID.randomUUID());
        var builder = new PageContextBuilderV1(new ArtifactContentReader(root), AiTestFixtures.properties("http://unused", "key"));
        PageContext first = builder.build(snapshot);
        PageContext second = builder.build(snapshot);
        assertThat(first).isEqualTo(second);
        assertThat(first.version()).isEqualTo("PAGE_CONTEXT_V1");
        assertThat(first.text()).contains("SANITIZED_HTML", "ACCESSIBILITY_STRUCTURE").doesNotContain("<script>", "axe-results");
        assertThat(first.sha256()).hasSize(64);
    }
}
