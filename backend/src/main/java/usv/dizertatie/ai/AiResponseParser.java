package usv.dizertatie.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.Finding;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the raw JSON string returned by the AI model into a list of {@link Finding} entities.
 *
 * <p>Handles malformed JSON, null input, and AI responses wrapped in markdown code fences
 * ({@code ```json ... ```}) gracefully — returning an empty list rather than throwing.
 */
@Component
@RequiredArgsConstructor
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    private final ObjectMapper objectMapper;

    /**
     * Parses the AI response JSON string into a list of {@link Finding} entities.
     *
     * <p>The expected JSON structure is:
     * <pre>{@code
     * {
     *   "findings": [
     *     {
     *       "wcagCriterion": "1.1.1",
     *       "wcagLevel": "A",
     *       "severity": "critical",
     *       "elementSelector": "img.hero",
     *       "description": "Images must have alternate text",
     *       "suggestion": "Add an alt attribute",
     *       "confidence": 0.95
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @param rawJson the raw JSON string from the AI model (may be null, empty, or markdown-fenced)
     * @return list of {@link Finding} entities with {@code sourceMethod} set to {@code "AI"};
     *         returns an empty list if the input is null/empty or the JSON is malformed
     */
    public List<Finding> parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("AI response JSON is null or empty — returning empty findings list");
            return List.of();
        }

        String json = stripMarkdownFences(rawJson.strip());

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode findingsNode = root.path("findings");

            if (!findingsNode.isArray()) {
                log.warn("AI response JSON does not contain a 'findings' array — returning empty list");
                return List.of();
            }

            List<Finding> findings = new ArrayList<>();
            for (JsonNode node : findingsNode) {
                findings.add(mapToFinding(node));
            }
            return findings;

        } catch (Exception e) {
            log.warn("Failed to parse AI response JSON: {} — returning empty findings list", e.getMessage());
            return List.of();
        }
    }

    /**
     * Strips {@code ```json} / {@code ```} markdown code fences from the AI response if present.
     */
    private String stripMarkdownFences(String text) {
        if (text.startsWith("```")) {
            // Remove opening fence (```json or ```)
            int newline = text.indexOf('\n');
            if (newline == -1) {
                return text;
            }
            text = text.substring(newline + 1);
            // Remove closing fence
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3).stripTrailing();
            }
        }
        return text;
    }

    private Finding mapToFinding(JsonNode node) {
        return Finding.builder()
                .sourceMethod("AI")
                .wcagCriterion(textOrNull(node, "wcagCriterion"))
                .wcagLevel(textOrNull(node, "wcagLevel"))
                .severity(textOrNull(node, "severity"))
                .elementSelector(textOrNull(node, "elementSelector"))
                .description(textOrNull(node, "description"))
                .suggestion(textOrNull(node, "suggestion"))
                .confidence(doubleOrNull(node, "confidence"))
                .build();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private Double doubleOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asDouble();
    }
}
