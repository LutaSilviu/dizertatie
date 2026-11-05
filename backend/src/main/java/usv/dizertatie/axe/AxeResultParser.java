package usv.dizertatie.axe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.wcag.WcagCriterionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses a raw Axe violations JSON array into a list of {@link Finding} entities.
 *
 * <p>Each violation in the JSON may contain zero or more {@code nodes} entries.
 * One {@link Finding} is produced per node (or one per violation if nodes is empty).
 */
@Component
public class AxeResultParser {

    private static final Pattern WCAG_CRITERION_TAG = Pattern.compile("^wcag(\\d)(\\d+)$");

    /** Conformance-level tags: wcag2a, wcag2aa, wcag2aaa, wcag21a, wcag21aa, wcag22aa, ... */
    private static final Pattern WCAG_LEVEL_TAG = Pattern.compile("^wcag2\\d?(a{1,3})$");

    private final ObjectMapper objectMapper;
    private final WcagCriterionRegistry registry;

    @Autowired
    public AxeResultParser(ObjectMapper objectMapper, WcagCriterionRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    /** Without a registry the level is read from axe's tags only. */
    public AxeResultParser(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    /**
     * Parses the Axe violations JSON array string into a list of findings.
     *
     * @param rawJson the JSON string (array of violation objects) returned by axe.run()
     * @return list of {@link Finding} entities (without scan set — caller must set scan before persisting)
     * @throws AxeParseException if the JSON cannot be parsed
     */
    public List<Finding> parse(String rawJson) {
        List<Finding> findings = new ArrayList<>();
        try {
            JsonNode violations = objectMapper.readTree(rawJson);
            for (JsonNode violation : violations) {
                findings.addAll(parseViolation(violation));
            }
        } catch (Exception e) {
            throw new AxeParseException("Failed to parse Axe violations JSON", e);
        }
        return findings;
    }

    private List<Finding> parseViolation(JsonNode violation) {
        String id = violation.path("id").asText("");
        String description = violation.path("description").asText("");
        String impact = violation.path("impact").asText("minor");
        JsonNode tags = violation.path("tags");
        JsonNode nodes = violation.path("nodes");

        String wcagCriterion = extractWcagCriterion(tags, id);
        String wcagLevel = resolveWcagLevel(wcagCriterion, tags);

        List<Finding> findings = new ArrayList<>();

        if (nodes.isArray() && nodes.size() > 0) {
            for (JsonNode node : nodes) {
                String elementSelector = extractSelector(node);
                findings.add(buildFinding(wcagCriterion, wcagLevel, impact, elementSelector, description));
            }
        } else {
            // No nodes — produce one finding with empty selector
            findings.add(buildFinding(wcagCriterion, wcagLevel, impact, "", description));
        }

        return findings;
    }

    private String extractWcagCriterion(JsonNode tags, String fallback) {
        if (tags.isArray()) {
            for (JsonNode tag : tags) {
                String tagText = tag.asText("");
                var matcher = WCAG_CRITERION_TAG.matcher(tagText);
                if (matcher.matches()) {
                    // "wcag111" → "1.1.1", "wcag143" → "1.4.3"
                    String digits = tagText.substring(4); // strip "wcag"
                    return formatWcagCriterion(digits);
                }
            }
        }
        return fallback;
    }

    /**
     * Converts a raw digit string from a wcag tag into dotted notation. Principle and guideline
     * are single digits; the rest is the criterion number, which can have two digits.
     * E.g. "111" → "1.1.1", "143" → "1.4.3", "1410" → "1.4.10", "258" → "2.5.8", "21" → "2.1"
     */
    static String formatWcagCriterion(String digits) {
        if (digits.length() >= 3) {
            return digits.charAt(0) + "." + digits.charAt(1) + "." + digits.substring(2);
        } else if (digits.length() == 2) {
            return digits.charAt(0) + "." + digits.charAt(1);
        }
        return digits;
    }

    /**
     * The criterion's level per the WCAG 2.2 registry (e.g. 2.5.8 Target Size is AA). Falls back
     * to axe's level tags, and to "A" when there is neither.
     */
    private String resolveWcagLevel(String criterion, JsonNode tags) {
        if (registry != null && criterion != null) {
            var level = registry.getLevel(criterion);
            if (level.isPresent()) {
                return level.get().name();
            }
        }
        String fromTags = null;
        if (tags.isArray()) {
            for (JsonNode tag : tags) {
                var m = WCAG_LEVEL_TAG.matcher(tag.asText(""));
                if (m.matches()) {
                    String level = m.group(1).toUpperCase(java.util.Locale.ROOT);
                    if (fromTags == null || level.length() > fromTags.length()) {
                        fromTags = level;
                    }
                }
            }
        }
        return fromTags != null ? fromTags : "A";
    }

    private String extractSelector(JsonNode node) {
        JsonNode target = node.path("target");
        if (target.isArray() && target.size() > 0) {
            return target.get(0).asText("");
        }
        return "";
    }

    private Finding buildFinding(String wcagCriterion, String wcagLevel,
                                  String severity, String elementSelector,
                                  String description) {
        return Finding.builder()
                .sourceMethod("AXE")
                .wcagCriterion(wcagCriterion)
                .wcagLevel(wcagLevel)
                .severity(severity)
                .elementSelector(elementSelector)
                .description(description)
                .build();
    }
}
