package usv.dizertatie.axe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
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
            JsonNode result = objectMapper.readTree(rawJson);
            JsonNode violations = result.isArray() ? result : result.path("violations");
            for (JsonNode violation : violations) {
                findings.addAll(parseViolation(violation));
            }
        } catch (Exception e) {
            throw new AxeParseException("Failed to parse Axe violations JSON", e);
        }
        return findings;
    }

    /** Parses axe-core results that require human review without treating them as violations. */
    public List<ManualReviewItem> parseIncomplete(String rawJson) {
        try {
            JsonNode result = objectMapper.readTree(rawJson);
            JsonNode incomplete = result.isArray() ? result : result.path("incomplete");
            if (!incomplete.isArray()) return List.of();
            List<ManualReviewItem> items = new ArrayList<>();
            for (JsonNode rule : incomplete) {
                List<ManualReviewElement> elements = new ArrayList<>();
                JsonNode nodes = rule.path("nodes");
                if (nodes.isArray()) {
                    for (JsonNode node : nodes) {
                        elements.add(new ManualReviewElement(
                                node.path("target").isArray()
                                        ? String.join(" ", StreamSupport.stream(node.path("target").spliterator(), false)
                                                .map(JsonNode::asText).toList())
                                        : "",
                                node.path("html").asText(""),
                                node.path("failureSummary").asText("")
                        ));
                    }
                }
                items.add(new ManualReviewItem(
                        rule.path("id").asText(""),
                        rule.path("description").asText(""),
                        rule.path("impact").asText(""),
                        rule.path("help").asText(""),
                        rule.path("helpUrl").asText(""),
                        List.copyOf(elements)
                ));
            }
            return items;
        } catch (Exception e) {
            throw new AxeParseException("Failed to parse Axe incomplete results", e);
        }
    }

    /** Stores only the incomplete portion of axe's result for the persisted scan. */
    public String incompleteAsJson(String rawJson) {
        try {
            JsonNode result = objectMapper.readTree(rawJson);
            JsonNode incomplete = result.isArray() ? objectMapper.createArrayNode() : result.path("incomplete");
            if (!incomplete.isArray()) incomplete = objectMapper.createArrayNode();
            return objectMapper.writeValueAsString(incomplete);
        } catch (Exception e) {
            throw new AxeParseException("Failed to extract Axe incomplete results", e);
        }
    }

    private List<Finding> parseViolation(JsonNode violation) {
        String id = violation.path("id").asText("");
        String description = violation.path("description").asText("");
        String impact = violation.path("impact").asText("minor");
        JsonNode tags = violation.path("tags");
        JsonNode nodes = violation.path("nodes");

        String wcagCriterion = extractWcagCriterion(tags);
        String wcagLevel = extractWcagLevel(tags);

        List<Finding> findings = new ArrayList<>();

        if (nodes.isArray() && nodes.size() > 0) {
            for (JsonNode node : nodes) {
                String elementSelector = extractSelector(node);
                findings.add(buildFinding(id, wcagCriterion, wcagLevel, impact, elementSelector, description));
            }
        } else {
            // No nodes — produce one finding with empty selector
            findings.add(buildFinding(id, wcagCriterion, wcagLevel, impact, "", description));
        }

        return findings;
    }

    private String extractWcagCriterion(JsonNode tags) {
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
        return null;
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

    private Finding buildFinding(String ruleId, String wcagCriterion, String wcagLevel,
                                  String severity, String elementSelector,
                                  String description) {
        return Finding.builder()
                .sourceMethod("AXE")
                .ruleId(ruleId)
                .wcagCriterion(wcagCriterion)
                .wcagLevel(wcagLevel)
                .severity(severity)
                .elementSelector(elementSelector)
                .description(description)
                .build();
    }
}
