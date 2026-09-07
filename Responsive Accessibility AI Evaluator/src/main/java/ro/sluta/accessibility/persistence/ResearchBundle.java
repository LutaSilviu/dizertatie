package ro.sluta.accessibility.persistence;

import java.util.List;
import ro.sluta.accessibility.domain.ExperimentEvaluation;
import ro.sluta.accessibility.domain.GroundTruthIssue;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.PredictedFinding;

public record ResearchBundle(AnalysisRecord analysis, List<RunRecord> runs, List<PageSnapshot> snapshots,
        List<StoredArtifact> artifacts, List<RawResultRecord> rawResults,
        List<NormalizationBatchRecord> normalizationBatches,
        List<PredictedFinding> findings, List<ExperimentConfigurationRecord> experimentConfigurations,
        List<GroundTruthIssue> groundTruthIssues, List<ExperimentEvaluation> evaluations) {
    public ResearchBundle {
        runs = copy(runs); snapshots = copy(snapshots); artifacts = copy(artifacts); rawResults = copy(rawResults);
        normalizationBatches = copy(normalizationBatches); findings = copy(findings);
        experimentConfigurations = copy(experimentConfigurations); groundTruthIssues = copy(groundTruthIssues);
        evaluations = copy(evaluations);
    }
    private static <T> List<T> copy(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
}
