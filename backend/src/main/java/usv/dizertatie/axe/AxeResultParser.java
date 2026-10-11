package usv.dizertatie.axe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.Finding;

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
@RequiredArgsConstructor
public class AxeResultParser {

    private static final Pattern WCAG_CRITERION_TAG = Pattern.compile("^wcag(\\d)(\\d+)$");

    private final ObjectMapper objectMapper;

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
        String wcagLevel = extractWcagLevel(tags);

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
     * Converts a raw digit string from a wcag tag into dotted notation.
     * E.g. "111" → "1.1.1", "143" → "1.4.3", "21" → "2.1"
     */
    private String formatWcagCriterion(String digits) {
        if (digits.length() == 3) {
            return digits.charAt(0) + "." + digits.charAt(1) + "." + digits.charAt(2);
        } else if (digits.length() == 2) {
            return digits.charAt(0) + "." + digits.charAt(1);
        }
        return digits;
    }

    private String extractWcagLevel(JsonNode tags) {
        if (tags.isArray()) {
            for (JsonNode tag : tags) {
                String tagText = tag.asText("");
                if ("wcag2aaa".equals(tagText)) return "AAA";
                if ("wcag2aa".equals(tagText))  return "AA";
                if ("wcag2a".equals(tagText))   return "A";
            }
        }
        return "A";
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
