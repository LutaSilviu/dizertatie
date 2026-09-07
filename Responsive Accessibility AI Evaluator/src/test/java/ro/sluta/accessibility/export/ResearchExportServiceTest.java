package ro.sluta.accessibility.export;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import ro.sluta.accessibility.domain.*;
import ro.sluta.accessibility.normalization.F5TestFixtures;
import ro.sluta.accessibility.normalization.FindingNormalizerV1;
import ro.sluta.accessibility.persistence.*;

class ResearchExportServiceTest {
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().findModulesViaServiceLoader(true).build();
    private final ResearchExportService exports = new ResearchExportService(mapper);

    @Test void jsonIsVersionedStableAndRedactsSecretsWhileKeepingUsageAndArtifactReferences() throws Exception {
        ResearchBundle bundle = bundle();
        byte[] first = exports.json(bundle), second = exports.json(bundle);
        assertThat(first).isEqualTo(second);
        String json = new String(first, StandardCharsets.UTF_8);
        assertThat(json).contains("\"schemaVersion\" : \"RESEARCH_EXPORT_SCHEMA_V1\"",
                "\"exportVersion\" : \"EXPORT_V1\"", "relativePath", "inputTokens");
        assertThat(json).doesNotContain("super-secret-value", "Bearer private-token", "sk-test-secret-value",
                "private-access-token");
        assertThat(json).contains("[REDACTED]");
        assertThat(mapper.readTree(first).path("runs").get(0).path("inputTokens").asLong()).isEqualTo(100);
    }

    @Test void csvExportsHaveStableUtf8Rfc4180HeadersDecimalPointsAndBlankFindingForFnTn() throws Exception {
        ResearchBundle bundle = bundle();
        String findings = text(exports.findingsCsv(bundle));
        String evaluations = text(exports.evaluationsCsv(bundle));
        String runs = text(exports.runsCsv(bundle));
        assertThat(findings).startsWith("findingId,runId,snapshotId,viewport,source,title,")
                .contains("\"Title, with comma\"").endsWith("\r\n");
        assertThat(evaluations).startsWith("evaluationId,findingId,runId,").contains(",,", ",GT-1,FN,");
        assertThat(runs).startsWith("runId,analysisId,method,modelId,").contains("0.0000123400");
        assertThat(findings.getBytes(StandardCharsets.UTF_8)).isEqualTo(exports.findingsCsv(bundle));
    }

    private ResearchBundle bundle() throws Exception {
        Instant now = Instant.parse("2026-09-01T00:00:00Z"); UUID analysisId = UUID.randomUUID(), runId = UUID.randomUUID();
        AnalysisRecord analysis = new AnalysisRecord(analysisId, null, "https://example.com",
                "{\"apiKey\":\"super-secret-value\",\"accessToken\":\"private-access-token\","
                        + "\"header\":\"Bearer private-token\",\"other\":\"sk-test-secret-value\"}",
                AnalysisStatus.COMPLETED, 100, now, now, 0);
        RunRecord run = new RunRecord(runId, analysisId, AnalysisMethod.AI, "GPT5_NANO", "AI_MULTIMODAL", 1,
                RunStatus.SUCCESS, now, now.plusSeconds(1), 1000L, 100L, 20L, 30L,
                new BigDecimal("0.0000123400"), true, null, null, "storage/runs/x/ai/response.json", 0);
        var finding = new FindingNormalizerV1().normalizeAi(F5TestFixtures.ai(Viewport.DESKTOP,
                "Title, with comma", "img.hero"), Viewport.DESKTOP).getFirst();
        ExperimentEvaluation fn = new ExperimentEvaluation(UUID.randomUUID(), Optional.empty(), runId, "GT-1",
                DetectionClass.FN, null, null, null, null, null, null, null, null, "reviewer", "line one, line two");
        StoredArtifact artifact = new StoredArtifact(UUID.randomUUID(), runId, null,
                new ArtifactReference(ArtifactType.AI_RESPONSE_JSON, "storage/runs/x/ai/response.json", "application/json",
                        42, "a".repeat(64), "AI_FINDINGS_SCHEMA_V1"), now);
        RawResultRecord raw = new RawResultRecord(UUID.randomUUID(), runId, "AI", artifact.artifactId(), null,
                "resp_test", "gpt-5-nano", now);
        return new ResearchBundle(analysis, List.of(run), List.of(), List.of(artifact), List.of(raw), List.of(), List.of(finding),
                List.of(), List.of(), List.of(fn));
    }
    private static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
}
