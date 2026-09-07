package ro.sluta.accessibility.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import ro.sluta.accessibility.domain.ExperimentEvaluation;
import ro.sluta.accessibility.domain.PredictedFinding;
import ro.sluta.accessibility.persistence.ResearchBundle;
import ro.sluta.accessibility.persistence.RunRecord;

@Service
public class ResearchExportService {
    public static final String EXPORT_VERSION = "EXPORT_V1";
    public static final String SCHEMA_VERSION = "RESEARCH_EXPORT_SCHEMA_V1";
    private static final List<String> SECRET_NAMES = List.of("authorization", "api_key", "apikey", "password", "secret", "cookie");
    private final ObjectMapper mapper;

    public ResearchExportService(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] json(ResearchBundle bundle) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION); root.put("exportVersion", EXPORT_VERSION);
        root.put("analysis", bundle.analysis()); root.put("runs", bundle.runs()); root.put("snapshots", bundle.snapshots());
        root.put("artifacts", bundle.artifacts()); root.put("rawResults", bundle.rawResults());
        root.put("normalizationBatches", bundle.normalizationBatches());
        root.put("predictedFindings", bundle.findings()); root.put("experimentConfigurations", bundle.experimentConfigurations());
        root.put("groundTruthIssues", bundle.groundTruthIssues()); root.put("experimentEvaluations", bundle.evaluations());
        try {
            JsonNode sanitized = sanitize(mapper.valueToTree(root));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(sanitized);
        } catch (Exception exception) { throw new IllegalStateException("Exportul JSON a eșuat.", exception); }
    }

    public byte[] findingsCsv(ResearchBundle bundle) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("findingId","runId","snapshotId","viewport","source","title","primaryWcagCriterion",
                "wcagCriteria","severity","displaySeverity","selector","elementFingerprint","confidence",
                "findingKey","duplicateCount","rawReference","normalizerVersion"));
        for (PredictedFinding value : bundle.findings()) rows.add(List.of(
                text(value.findingId()), text(value.runId()), text(value.snapshotId()), text(value.viewport()), text(value.source()),
                text(value.title()), text(value.primaryWcagCriterion()), String.join("|", value.wcagCriteria()),
                text(value.severity()), text(value.displaySeverity()), value.location() == null ? "" : text(value.location().selector()),
                text(value.elementFingerprint()), number(value.confidence()), text(value.findingKey()),
                Integer.toString(value.duplicateCount()), text(value.rawReference()), text(value.normalizerVersion())));
        return csv(rows);
    }

    public byte[] evaluationsCsv(ResearchBundle bundle) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("evaluationId","findingId","runId","matchedGroundTruthId","detectionClass","localizationScore",
                "wcagScore","e1","e2","e3","e4","e5","qualityScore","reviewer","notes"));
        for (ExperimentEvaluation value : bundle.evaluations()) rows.add(List.of(
                text(value.evaluationId()), value.findingId().map(Object::toString).orElse(""), text(value.runId()),
                text(value.matchedGroundTruthId()), text(value.detectionClass()), number(value.localizationScore()),
                number(value.wcagScore()), number(value.e1()), number(value.e2()), number(value.e3()), number(value.e4()),
                number(value.e5()), number(value.qualityScore()), text(value.reviewer()), text(value.notes())));
        return csv(rows);
    }

    public byte[] runsCsv(ResearchBundle bundle) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("runId","analysisId","method","modelId","condition","repetition","status","startedAt",
                "completedAt","latencyMs","inputTokens","cachedInputTokens","outputTokens","costUsd","schemaValid",
                "errorCode","errorMessage","rawResultRef"));
        for (RunRecord value : bundle.runs()) rows.add(List.of(text(value.runId()), text(value.analysisId()),
                text(value.method()), text(value.modelId()), text(value.condition()), Integer.toString(value.repetition()),
                text(value.status()), text(value.startedAt()), text(value.completedAt()), number(value.latencyMs()),
                number(value.inputTokens()), number(value.cachedInputTokens()), number(value.outputTokens()),
                value.costUsd() == null ? "" : value.costUsd().toPlainString(), text(value.schemaValid()),
                text(value.errorCode()), text(value.errorMessage()), text(value.rawResultRef())));
        return csv(rows);
    }

    private JsonNode sanitize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>(); object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                String normalized = name.toLowerCase(Locale.ROOT).replace("-", "_");
                String compact = normalized.replace("_", "");
                if (SECRET_NAMES.stream().anyMatch(normalized::contains) || compact.equals("token")
                        || compact.equals("accesstoken") || compact.equals("sessiontoken")
                        || compact.equals("apikey")) object.put(name, "[REDACTED]");
                else object.set(name, sanitize(object.get(name)));
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(index, sanitize(node.get(index)));
        }
        else if (node.isTextual()) {
            String value = node.asText().replaceAll("(?i)bearer\\s+[a-z0-9._~+/-]+=*", "Bearer [REDACTED]")
                    .replaceAll("sk-[A-Za-z0-9_-]{10,}", "[REDACTED]")
                    .replaceAll("(?i)(\\\"?(?:api[_-]?key|access[_-]?token|session[_-]?token|password|secret|authorization|cookie)\\\"?\\s*[:=]\\s*\\\")[^\\\"]*(\\\")", "$1[REDACTED]$2");
            return mapper.getNodeFactory().textNode(value);
        }
        return node;
    }

    private static byte[] csv(List<List<String>> rows) {
        StringBuilder value = new StringBuilder();
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) value.append(','); value.append(escape(row.get(index)));
            }
            value.append("\r\n");
        }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static String escape(String value) {
        String safe = value == null ? "" : value;
        return safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\r') >= 0 || safe.indexOf('\n') >= 0
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static String number(Number value) { return value == null ? "" : value.toString(); }
}
