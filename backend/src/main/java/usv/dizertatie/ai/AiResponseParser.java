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
 * <p>Accepts replies wrapped in markdown code fences ({@code ```json ... ```}). A reply that is
 * empty, not JSON, or has no {@code findings} array is <em>invalid</em> and raises
 * {@link AiResponseFormatException}: it must never be read as "the model found nothing". Only a
 * well-formed reply with {@code "findings": []} yields an empty list.
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
     *         empty only when the model explicitly reported no findings
     * @throws AiResponseFormatException if the reply is empty, not JSON, or lacks a findings array
     */
    public List<Finding> parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new AiResponseFormatException("The AI model returned an empty reply.");
        }

        String json = stripMarkdownFences(rawJson.strip());

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("AI reply is not valid JSON: {}", e.getMessage());
            throw new AiResponseFormatException("The AI model's reply was not valid JSON.", e);
        }
        JsonNode findingsNode = root == null ? null : root.path("findings");
        if (findingsNode == null || !findingsNode.isArray()) {
            log.warn("AI reply has no 'findings' array");
            throw new AiResponseFormatException("The AI model's reply did not contain a findings list.");
        }

        List<Finding> findings = new ArrayList<>();
        for (JsonNode node : findingsNode) {
            if (node.isObject()) {
                findings.add(mapToFinding(node));
            }
        }
        return findings;
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
