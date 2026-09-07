package ro.sluta.accessibility.analysis.ai;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.browser.ArtifactContentReader;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.config.AiAnalysisProperties;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.ArtifactType;
import ro.sluta.accessibility.domain.PageSnapshot;

@Component
public class PageContextBuilderV1 {
    private final ArtifactContentReader reader;
    private final AiAnalysisProperties properties;

    public PageContextBuilderV1(ArtifactContentReader reader, AiAnalysisProperties properties) {
        this.reader = reader;
        this.properties = properties;
    }

    public PageContext build(PageSnapshot snapshot) {
        String html = text(snapshot, ArtifactType.PAGE_HTML);
        String aria = text(snapshot, ArtifactType.ARIA_YAML);
        String sanitized = sanitize(html);
        String context = "SNAPSHOT\nrequestedUrl=" + snapshot.requestedUrl()
                + "\nfinalUrl=" + snapshot.finalUrl()
                + "\nviewport=" + snapshot.viewport().width() + "x" + snapshot.viewport().height()
                + "\ninteractionState=" + snapshot.interactionState()
                + "\n\nSANITIZED_HTML\n" + sanitized
                + "\n\nACCESSIBILITY_STRUCTURE\n" + redact(aria);
        int original = context.length();
        boolean truncated = original > properties.contextMaxCharacters();
        String retained = truncated ? context.substring(0, properties.contextMaxCharacters()) : context;
        byte[] bytes = retained.getBytes(StandardCharsets.UTF_8);
        return new PageContext(properties.contextVersion(), retained, original, retained.length(), truncated,
                AtomicArtifactStore.sha256(bytes));
    }

    private String text(PageSnapshot snapshot, ArtifactType type) {
        return snapshot.artifacts().stream()
                .filter(reference -> reference.artifactType() == type)
                .sorted(Comparator.comparing(ArtifactReference::relativePath))
                .findFirst().map(reader::readVerified)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8)).orElse("");
    }

    static String sanitize(String input) {
        String value = input == null ? "" : input;
        value = value.replaceAll("(?is)<!--.*?-->", "");
        value = value.replaceAll("(?is)<(script|style|template)[^>]*>.*?</\\1\\s*>", "");
        value = value.replaceAll("(?is)<[^>]+(?:hidden|aria-hidden\\s*=\\s*['\"]?true['\"]?)[^>]*>.*?</[^>]+>", "");
        value = value.replaceAll("(?i)(authorization|api[-_]?key|access[-_]?token|password|secret)\\s*[:=]\\s*[^\\s\"'<>]+", "$1=[REDACTED]");
        value = value.replaceAll("(?i)bearer\\s+[a-z0-9._~+/-]+=*", "Bearer [REDACTED]");
        return value.replaceAll("[\\t ]+", " ").replaceAll("(?m)^\\s+$", "").trim();
    }

    static String redact(String input) {
        String value = sanitize(input).replaceAll("(?i)(cookie|session)[^\\n]{0,200}", "$1: [REDACTED]");
        return value.toLowerCase(Locale.ROOT).contains("openai_api_key") ? "[REDACTED_CONTEXT]" : value;
    }
}
