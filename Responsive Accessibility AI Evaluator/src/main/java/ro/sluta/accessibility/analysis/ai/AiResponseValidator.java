package ro.sluta.accessibility.analysis.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiResponseValidator {
    private static final Set<String> ROOT = Set.of("analysisSummary", "limitations", "findings");
    private static final Set<String> FINDING = Set.of("title", "description", "wcagCriteria", "conformanceLevel",
            "severity", "element", "explanation", "impact", "recommendation", "confidence", "evidence");
    private static final Set<String> ELEMENT = Set.of("selector", "htmlSnippet", "role", "accessibleName", "visibleText");
    private static final Set<String> LEVELS = Set.of("A", "AA", "UNKNOWN");
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "SERIOUS", "MODERATE", "MINOR", "UNKNOWN");
    private static final Pattern WCAG = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");
    private final ObjectMapper objectMapper;

    public AiResponseValidator(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public Validation validate(String json) {
        if (json == null || json.isBlank()) return Validation.invalid("Răspuns gol.");
        try {
            JsonNode root = objectMapper.readTree(json);
            requireObject(root, ROOT, "root");
            requireText(root, "analysisSummary");
            requireStringArray(root.get("limitations"), 10, "limitations", false);
            JsonNode findings = root.get("findings");
            if (findings == null || !findings.isArray() || findings.size() > 100) fail("findings invalid.");
            for (JsonNode finding : findings) validateFinding(finding);
            return Validation.valid(root);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Validation.invalid(exception.getMessage());
        }
    }

    private void validateFinding(JsonNode finding) {
        requireObject(finding, FINDING, "finding");
        for (String field : List.of("title", "description", "explanation", "impact", "recommendation")) requireText(finding, field);
        requireStringArray(finding.get("wcagCriteria"), Integer.MAX_VALUE, "wcagCriteria", true);
        finding.get("wcagCriteria").forEach(value -> { if (!WCAG.matcher(value.textValue()).matches()) fail("Criteriu WCAG invalid."); });
        if (!LEVELS.contains(text(finding, "conformanceLevel"))) fail("Nivel de conformitate invalid.");
        if (!SEVERITIES.contains(text(finding, "severity"))) fail("Severitate invalidă.");
        JsonNode element = finding.get("element");
        requireObject(element, ELEMENT, "element");
        ELEMENT.forEach(field -> { JsonNode value = element.get(field); if (!(value.isNull() || value.isTextual())) fail("Câmp element invalid: " + field); });
        JsonNode confidence = finding.get("confidence");
        if (confidence == null || !confidence.isNumber() || confidence.doubleValue() < 0 || confidence.doubleValue() > 1) fail("Confidence invalid.");
        requireStringArray(finding.get("evidence"), 5, "evidence", false);
    }

    private static void requireObject(JsonNode node, Set<String> fields, String name) {
        if (node == null || !node.isObject()) fail(name + " trebuie să fie obiect.");
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) if (!fields.contains(names.next())) fail(name + " conține proprietăți suplimentare.");
        fields.forEach(field -> { if (!node.has(field)) fail(name + " nu conține " + field); });
    }

    private static void requireText(JsonNode node, String field) { text(node, field); }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) fail("Câmp text invalid: " + field);
        return value.textValue();
    }
    private static void requireStringArray(JsonNode node, int max, String name, boolean allowEmpty) {
        if (node == null || !node.isArray() || node.size() > max || (!allowEmpty && node.size() < 0)) fail(name + " invalid.");
        node.forEach(value -> { if (!value.isTextual()) fail(name + " trebuie să conțină texte."); });
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }

    public record Validation(boolean valid, JsonNode parsed, String error) {
        static Validation valid(JsonNode parsed) { return new Validation(true, parsed, null); }
        static Validation invalid(String error) { return new Validation(false, null, error); }
    }
}
