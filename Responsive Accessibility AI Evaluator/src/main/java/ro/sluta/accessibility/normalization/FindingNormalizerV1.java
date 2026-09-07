package ro.sluta.accessibility.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.analysis.ai.AiAnalysisResult;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisResult;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.domain.*;

@Component
public class FindingNormalizerV1 {
    public static final String VERSION = "NORMALIZER_V1";
    private static final Pattern AXE_WCAG = Pattern.compile("^wcag([1-9])([0-9])([0-9]+)$", Pattern.CASE_INSENSITIVE);
    private static final Comparator<String> WCAG_ORDER = Comparator.comparing(FindingNormalizerV1::criterionParts,
            (left, right) -> {
                for (int i = 0; i < Math.max(left.length, right.length); i++) {
                    int a = i < left.length ? left[i] : 0;
                    int b = i < right.length ? right[i] : 0;
                    if (a != b) return Integer.compare(a, b);
                }
                return 0;
            });

    public List<PredictedFinding> normalizeAxe(AxeAnalysisResult result) {
        List<PredictedFinding> findings = new ArrayList<>();
        String raw = artifact(result.artifacts(), ArtifactType.AXE_RESULTS_JSON);
        int sourceIndex = 0;
        for (var rule : result.violations()) {
            List<String> criteria = axeCriteria(rule.tags());
            for (int nodeIndex = 0; nodeIndex < rule.nodes().size(); nodeIndex++) {
                var node = rule.nodes().get(nodeIndex);
                String selector = normalizeSelector(node.target());
                FindingLocation location = new FindingLocation(selector, node.html(), null, null, null, selector);
                CanonicalSeverity severity = severity(node.impact() == null ? rule.impact() : node.impact());
                String category = normalizeCategory(rule.id());
                String fingerprint = fingerprint(result.snapshotId(), location);
                String key = key(result.viewport(), criteria, fingerprint, category);
                var ref = new FindingSourceReference(FindingSource.AXE_ONLY, rule.id(), raw, sourceIndex++);
                findings.add(new PredictedFinding(id(key), result.runId(), result.snapshotId(), result.viewport(),
                        FindingSource.AXE_ONLY, List.of(ref), value(rule.help(), rule.id()), rule.description(),
                        first(criteria), criteria, severity, severity, null, severity,
                        level(rule.tags()), category, fingerprint, location, list(node.failureSummary()),
                        rule.description(), node.failureSummary(), null, raw, key, 1, null, VERSION));
            }
        }
        return deduplicate(findings);
    }

    public List<PredictedFinding> normalizeAi(AiAnalysisResult result, Viewport viewport) {
        if (!result.success() || result.parsed() == null) return List.of();
        List<PredictedFinding> findings = new ArrayList<>();
        String raw = artifact(result.artifacts(), ArtifactType.AI_PARSED_JSON);
        JsonNode array = result.parsed().path("findings");
        for (int index = 0; index < array.size(); index++) {
            JsonNode item = array.get(index);
            List<String> extractedCriteria = new ArrayList<>();
            item.path("wcagCriteria").forEach(value -> extractedCriteria.add(value.asText()));
            List<String> criteria = extractedCriteria.stream().distinct().sorted(WCAG_ORDER).toList();
            JsonNode element = item.path("element");
            FindingLocation location = new FindingLocation(nullable(element, "selector"), nullable(element, "htmlSnippet"),
                    nullable(element, "role"), nullable(element, "accessibleName"), nullable(element, "visibleText"), null);
            String category = normalizeCategory(item.path("title").asText("unknown"));
            String fingerprint = fingerprint(result.snapshotId(), location);
            String key = key(viewport, criteria, fingerprint, category);
            CanonicalSeverity severity = severity(item.path("severity").asText(null));
            var ref = new FindingSourceReference(FindingSource.AI_ONLY, result.providerResponseId(), raw, index);
            findings.add(new PredictedFinding(id(key), result.runId(), result.snapshotId(), viewport,
                    FindingSource.AI_ONLY, List.of(ref), item.path("title").asText("Constatare AI"),
                    item.path("description").asText(null), first(criteria), criteria, severity, null, severity, severity,
                    level(item.path("conformanceLevel").asText(null)), category, fingerprint, location,
                    list(item.path("explanation").asText(null)), item.path("impact").asText(null),
                    item.path("recommendation").asText(null), item.path("confidence").isNumber()
                            ? item.path("confidence").doubleValue() : null,
                    raw, key, 1, null, VERSION));
        }
        return deduplicate(findings);
    }

    public List<PredictedFinding> deduplicate(List<PredictedFinding> input) {
        Map<String, PredictedFinding> unique = new LinkedHashMap<>();
        input.stream().sorted(Comparator.comparing(PredictedFinding::findingKey)).forEach(finding ->
                unique.merge(finding.findingKey(), finding, FindingNormalizerV1::mergeDuplicates));
        return List.copyOf(unique.values());
    }

    private static PredictedFinding mergeDuplicates(PredictedFinding first, PredictedFinding duplicate) {
        List<FindingSourceReference> refs = new ArrayList<>(first.sourceRefs()); refs.addAll(duplicate.sourceRefs());
        List<String> explanations = new ArrayList<>(new LinkedHashSet<>(first.explanations()));
        duplicate.explanations().stream().filter(value -> !explanations.contains(value)).forEach(explanations::add);
        return copy(first, first.source(), refs, first.severity(), first.axeSeverity(), first.aiSeverity(),
                first.displaySeverity(), explanations, first.duplicateCount() + duplicate.duplicateCount(), first.matchEvidence());
    }

    public static PredictedFinding copy(PredictedFinding f, FindingSource source, List<FindingSourceReference> refs,
            CanonicalSeverity severity, CanonicalSeverity axeSeverity, CanonicalSeverity aiSeverity,
            CanonicalSeverity displaySeverity, List<String> explanations, int duplicates, LocationMatchEvidence evidence) {
        return new PredictedFinding(f.findingId(), f.runId(), f.snapshotId(), f.viewport(), source, refs, f.title(),
                f.description(), f.primaryWcagCriterion(), f.wcagCriteria(), severity, axeSeverity, aiSeverity,
                displaySeverity, f.conformanceLevel(), f.normalizedCategory(), f.elementFingerprint(), f.location(),
                explanations, f.impact(), f.recommendation(), f.confidence(), f.rawReference(), f.findingKey(),
                duplicates, evidence, f.normalizerVersion());
    }

    static List<String> axeCriteria(List<String> tags) {
        Set<String> criteria = new LinkedHashSet<>();
        for (String tag : tags) {
            Matcher matcher = AXE_WCAG.matcher(tag);
            if (matcher.matches()) criteria.add(matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3));
        }
        return criteria.stream().sorted(WCAG_ORDER).toList();
    }

    static String fingerprint(UUID snapshotId, FindingLocation location) {
        return hash(snapshotId + "|" + normalize(location.selector()) + "|" + normalize(location.role()) + "|"
                + normalize(location.accessibleName()) + "|" + normalize(location.visibleText()));
    }

    static String key(Viewport viewport, List<String> criteria, String fingerprint, String category) {
        return hash(VERSION + "|" + viewport + "|" + String.join(",", criteria) + "|" + fingerprint + "|" + category);
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static String normalizeCategory(String value) { return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); }
    static String normalizeSelector(Object target) {
        if (target == null) return null;
        if (target instanceof List<?> list) return list.stream().map(String::valueOf).reduce((a, b) -> a + " >>> " + b).orElse(null);
        return String.valueOf(target).replaceAll("^\\[|]$", "").trim();
    }
    static CanonicalSeverity severity(String value) {
        if (value == null) return CanonicalSeverity.UNKNOWN;
        try { return CanonicalSeverity.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return CanonicalSeverity.UNKNOWN; }
    }
    static CanonicalConformanceLevel level(List<String> tags) {
        return tags.stream().map(String::toLowerCase).anyMatch(tag -> tag.endsWith("aa"))
                ? CanonicalConformanceLevel.AA : CanonicalConformanceLevel.A;
    }
    static CanonicalConformanceLevel level(String value) {
        try { return value == null ? CanonicalConformanceLevel.UNKNOWN : CanonicalConformanceLevel.valueOf(value); }
        catch (IllegalArgumentException exception) { return CanonicalConformanceLevel.UNKNOWN; }
    }
    static String artifact(List<ArtifactReference> artifacts, ArtifactType type) {
        return artifacts.stream().filter(a -> a.artifactType() == type).map(ArtifactReference::relativePath).findFirst().orElse(null);
    }
    static String nullable(JsonNode node, String field) { JsonNode value = node.get(field); return value == null || value.isNull() ? null : value.asText(); }
    static String first(List<String> values) { return values.isEmpty() ? null : values.getFirst(); }
    static List<String> list(String value) { return value == null || value.isBlank() ? List.of() : List.of(value); }
    static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    static UUID id(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)); }
    static String hash(String value) { return AtomicArtifactStore.sha256(value.getBytes(StandardCharsets.UTF_8)); }
    static int[] criterionParts(String value) {
        try { return java.util.Arrays.stream(value.split("\\.")).mapToInt(Integer::parseInt).toArray(); }
        catch (RuntimeException exception) { return new int[]{Integer.MAX_VALUE}; }
    }
}
