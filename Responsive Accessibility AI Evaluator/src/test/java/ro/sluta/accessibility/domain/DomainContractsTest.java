package ro.sluta.accessibility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DomainContractsTest {

    @Test
    void falseNegativeCanExistWithoutFinding() {
        ExperimentEvaluation evaluation = new ExperimentEvaluation(
                UUID.randomUUID(), Optional.empty(), UUID.randomUUID(), "S01-BAD-DESKTOP",
                DetectionClass.FN, null, null, null, null, null, null, null,
                null, "evaluator", "Problema nu a fost detectată.");

        assertThat(evaluation.findingId()).isEmpty();
        assertThat(evaluation.detectionClass()).isEqualTo(DetectionClass.FN);
    }

    @Test
    void domainContractsAreRecordsAndNotJpaEntities() {
        assertThat(Analysis.class.isRecord()).isTrue();
        assertThat(Run.class.isRecord()).isTrue();
        assertThat(PageSnapshot.class.isRecord()).isTrue();
        assertThat(PredictedFinding.class.isRecord()).isTrue();
        assertThat(GroundTruthIssue.class.isRecord()).isTrue();
        assertThat(ExperimentEvaluation.class.isRecord()).isTrue();
        assertThat(Analysis.class.getAnnotations()).isEmpty();
        assertThat(ExperimentEvaluation.class.getAnnotations()).isEmpty();
    }
}
