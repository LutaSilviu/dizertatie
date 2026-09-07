package ro.sluta.accessibility.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.domain.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResearchPersistenceServiceTest {
    @Autowired ResearchPersistenceService persistence;
    @Autowired JdbcClient jdbc;
    @Autowired Path snapshotStorageRoot;

    @Test void flywayCreatesTheCompleteSchemaOnH2() {
        Integer migrations = jdbc.sql("""
                select count(*) from "flyway_schema_history"
                where "success"=true and "version" in ('1','2')
                """)
                .query(Integer.class).single();
        assertThat(migrations).isEqualTo(2);
        for (String table : List.of("analysis_record", "analysis_run", "page_snapshot", "artifact", "raw_result",
                "predicted_finding", "finding_source_ref", "normalization_batch", "experiment_configuration",
                "ground_truth_issue", "experiment_evaluation", "experiment_plan", "experiment_execution",
                "evaluation_decision")) {
            Integer count = jdbc.sql("select count(*) from information_schema.tables where lower(table_name)=:name")
                    .param("name", table).query(Integer.class).single();
            assertThat(count).as(table).isEqualTo(1);
        }
    }

    @Test void savesAndReadsTheResearchAggregateWithoutLosingOptionalEvaluations() {
        Fixture f = fixture();
        ResearchBundle bundle = persistence.loadBundle(f.analysis.analysisId());
        assertThat(bundle.analysis()).isEqualTo(f.analysis);
        assertThat(bundle.runs()).containsExactly(f.run);
        assertThat(bundle.snapshots()).containsExactly(f.snapshot);
        assertThat(bundle.artifacts()).singleElement().satisfies(a -> assertThat(a.reference()).isEqualTo(f.artifact.reference()));
        assertThat(bundle.rawResults()).containsExactly(f.rawResult);
        assertThat(bundle.normalizationBatches()).containsExactly(f.batch);
        assertThat(bundle.findings()).containsExactly(f.finding);
        assertThat(bundle.experimentConfigurations()).contains(f.configuration);
        assertThat(bundle.groundTruthIssues()).contains(f.truth);
        assertThat(bundle.evaluations()).hasSize(2).anySatisfy(e -> {
            assertThat(e.detectionClass()).isEqualTo(DetectionClass.FN); assertThat(e.findingId()).isEmpty();
        });
    }

    @Test void historyIsPagedFilteredAndRepeatCreatesNewIdsWithoutOverwrite() {
        Fixture first = fixture();
        Fixture second = fixture();
        HistoryPage page = persistence.history(new HistoryFilter(null, null, "example.com", AnalysisMethod.AXE,
                null, AnalysisStatus.CREATED, 0, 1, true));
        assertThat(page.total()).isGreaterThanOrEqualTo(2);
        assertThat(page.items()).hasSize(1);

        AnalysisRecord repeated = persistence.repeat(first.analysis.analysisId(), Instant.parse("2026-09-01T12:00:00Z"));
        assertThat(repeated.analysisId()).isNotEqualTo(first.analysis.analysisId());
        assertThat(repeated.parentAnalysisId()).isEqualTo(first.analysis.analysisId());
        assertThat(repeated.configurationJson()).isEqualTo(first.analysis.configurationJson());
        ResearchBundle copied = persistence.loadBundle(repeated.analysisId());
        assertThat(copied.runs()).hasSize(1).allSatisfy(run -> {
            assertThat(run.runId()).isNotEqualTo(first.run.runId()); assertThat(run.status()).isEqualTo(RunStatus.PENDING);
        });
        assertThat(persistence.findAnalysis(first.analysis.analysisId())).contains(first.analysis);
    }

    @Test void optimisticVersionAndAbandonedRecoveryAreExplicitAndDuplicatesAreRejected() {
        Fixture f = fixture(RunStatus.RUNNING);
        AnalysisRecord updated = persistence.updateAnalysisStatus(f.analysis.analysisId(), 0, AnalysisStatus.ANALYZING, 50, Instant.now());
        assertThat(updated.version()).isEqualTo(1);
        assertThatThrownBy(() -> persistence.updateAnalysisStatus(f.analysis.analysisId(), 0, AnalysisStatus.COMPLETED, 100, Instant.now()))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(persistence.markAbandonedRuns()).isEqualTo(1);
        assertThat(persistence.loadBundle(f.analysis.analysisId()).runs().getFirst().status()).isEqualTo(RunStatus.ABANDONED);
        assertThatThrownBy(() -> persistence.saveEvaluation(f.fn)).isInstanceOf(DuplicateKeyException.class);
    }

    private Fixture fixture() { return fixture(RunStatus.SUCCESS); }
    private Fixture fixture(RunStatus runStatus) {
        Instant now = Instant.parse("2026-09-01T10:00:00Z");
        UUID analysisId = UUID.randomUUID(), runId = UUID.randomUUID(), snapshotId = UUID.randomUUID();
        AnalysisRecord analysis = new AnalysisRecord(analysisId, null, "https://example.com/page", "{\"model\":\"GPT5_NANO\"}",
                AnalysisStatus.CREATED, 0, now, now, 0);
        RunRecord run = new RunRecord(runId, analysisId, AnalysisMethod.AXE, null, null, 1, runStatus, now, now.plusSeconds(1),
                1000L, 0L, 0L, 0L, BigDecimal.ZERO, true, null, null, "storage/runs/raw.json", 0);
        PageSnapshot snapshot = new PageSnapshot(snapshotId, runId, URI.create("https://example.com/page"),
                URI.create("https://example.com/page"), now, Viewport.DESKTOP, null, null, null, List.of(), List.of(),
                SnapshotStatus.SUCCESS, List.of(), null, null, "f2-v1", "a".repeat(64), "none@1", List.of());
        persistence.saveAnalysis(analysis); persistence.saveRun(run); persistence.saveSnapshot(snapshot);

        ArtifactReference ref = new AtomicArtifactStore(snapshotStorageRoot).write(runId, snapshotId, "persisted.json",
                ArtifactType.METADATA_JSON, "application/json", "{}".getBytes(StandardCharsets.UTF_8), "test-v1");
        StoredArtifact artifact = new StoredArtifact(UUID.randomUUID(), runId, snapshotId, ref, now);
        persistence.saveArtifact(artifact);
        RawResultRecord rawResult = new RawResultRecord(UUID.randomUUID(), runId, "AXE", artifact.artifactId(), null,
                null, "axe-core@4.13.0", now);
        persistence.saveRawResult(rawResult);
        NormalizationBatchRecord batch = new NormalizationBatchRecord(UUID.randomUUID(), analysisId, "NORMALIZER_V1",
                null, "{}", "b".repeat(64), "c".repeat(64), false, now);
        persistence.saveNormalizationBatch(batch);
        FindingLocation location = new FindingLocation("img.hero", "<img>", "img", null, null, "html>body>img");
        PredictedFinding finding = new PredictedFinding(UUID.randomUUID(), runId, snapshotId, Viewport.DESKTOP,
                FindingSource.AXE_ONLY, List.of(new FindingSourceReference(FindingSource.AXE_ONLY, "image-alt", ref.relativePath(), 0)),
                "Image alt", "description", "1.1.1", List.of("1.1.1"), CanonicalSeverity.SERIOUS,
                CanonicalSeverity.SERIOUS, null, CanonicalSeverity.SERIOUS, CanonicalConformanceLevel.A,
                "image-alt", "d".repeat(64), location, List.of("explanation"), "impact", "recommendation", null,
                ref.relativePath(), "e".repeat(64), 1, null, "NORMALIZER_V1");
        persistence.saveFinding(batch.batchId(), finding);
        UUID configurationId = UUID.randomUUID();
        String configurationHash = configurationId.toString().replace("-", "").repeat(2);
        ExperimentConfigurationRecord configuration = new ExperimentConfigurationRecord(configurationId, "pilot",
                "v1-" + configurationId, "{\"prompt\":\"v1\"}", configurationHash, now, 0);
        persistence.saveExperimentConfiguration(configuration);
        UUID truthFixtureId = UUID.randomUUID();
        GroundTruthIssue truth = new GroundTruthIssue("GT-" + truthFixtureId, "S01", "BAD", Viewport.DESKTOP,
                "default", "img.hero", "Missing alt", "1.1.1", "A", GroundTruthIssue.ExpectedPresence.PRESENT,
                "manual", "fixture-v1-" + truthFixtureId, truthFixtureId.toString().replace("-", "").repeat(2));
        persistence.saveGroundTruth(truth);
        ExperimentEvaluation tp = new ExperimentEvaluation(UUID.randomUUID(), Optional.of(finding.findingId()), runId,
                truth.groundTruthId(), DetectionClass.TP, 2, 2, 2, 2, 2, 2, 2, 2.0, "reviewer", "matched");
        ExperimentEvaluation fn = new ExperimentEvaluation(UUID.randomUUID(), Optional.empty(), runId,
                truth.groundTruthId(), DetectionClass.FN, null, null, null, null, null, null, null, null, "reviewer", "missed");
        persistence.saveEvaluation(tp); persistence.saveEvaluation(fn);
        return new Fixture(analysis, run, snapshot, artifact, rawResult, batch, finding, configuration, truth, tp, fn);
    }

    private record Fixture(AnalysisRecord analysis, RunRecord run, PageSnapshot snapshot, StoredArtifact artifact,
            RawResultRecord rawResult,
            NormalizationBatchRecord batch, PredictedFinding finding, ExperimentConfigurationRecord configuration,
            GroundTruthIssue truth, ExperimentEvaluation tp, ExperimentEvaluation fn) { }
}
