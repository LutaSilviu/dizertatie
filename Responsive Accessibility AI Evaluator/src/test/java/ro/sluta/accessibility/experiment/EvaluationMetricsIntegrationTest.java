package ro.sluta.accessibility.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.sluta.accessibility.domain.*;
import ro.sluta.accessibility.persistence.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvaluationMetricsIntegrationTest {
    @Autowired ResearchPersistenceService research;
    @Autowired ExperimentRunnerService runner;
    @Autowired ExperimentPlanRepository plans;
    @Autowired EvaluationService evaluations;
    @Autowired EvaluationDecisionRepository decisionRepository;
    @Autowired ExperimentMetricsService metrics;
    @Autowired ExperimentPackageExportService exports;
    @Autowired MockMvc mvc;

    @Test void preservesIndividualAndAdjudicatedDecisionsAndRegeneratesMetricsAndExport() throws Exception {
        Instant now=Instant.parse("2026-09-01T10:00:00Z");UUID analysisId=UUID.randomUUID(),runId=UUID.randomUUID();
        research.saveAnalysis(new AnalysisRecord(analysisId,null,"http://localhost/benchmark/S01/BAD","{}",AnalysisStatus.CREATED,0,now,now,0));
        research.saveRun(new RunRecord(runId,analysisId,AnalysisMethod.AXE,null,null,1,RunStatus.SUCCESS,now,now,0L,0L,0L,0L,BigDecimal.ZERO,true,null,null,null,0));
        GroundTruthIssue first=truth("GT-EVAL-1","S01"),second=truth("GT-EVAL-2","S02");research.saveGroundTruth(first);research.saveGroundTruth(second);

        ExperimentPlan plan=runner.createPlan(new ExperimentFreezeRequest(701,1,new BigDecimal("1"),new BigDecimal("0.01"),List.of("S01","S04")),ExperimentMode.PILOT,false);
        PlannedExecution execution=runner.reserveNext(plan.planId(),false).orElseThrow();plans.complete(execution.executionId(),ExecutionStatus.SUCCESS,BigDecimal.ZERO,runId,null,now);

        EvaluationDecision one=evaluations.create(request(runId,first.groundTruthId(),DetectionClass.TP,"reviewer-a",EvaluationDecisionKind.INDIVIDUAL,List.of()));
        EvaluationDecision two=evaluations.create(request(runId,first.groundTruthId(),DetectionClass.TP,"reviewer-b",EvaluationDecisionKind.INDIVIDUAL,List.of()));
        EvaluationDecision adjudicated=evaluations.create(request(runId,first.groundTruthId(),DetectionClass.TP,"adjudicator",EvaluationDecisionKind.ADJUDICATED,List.of(one.decisionId(),two.decisionId())));
        EvaluationDecision fn=evaluations.create(request(runId,second.groundTruthId(),DetectionClass.FN,"reviewer-a",EvaluationDecisionKind.INDIVIDUAL,List.of()));
        assertThat(fn.findingId()).isEmpty();assertThat(decisionRepository.byRun(runId)).contains(one,two,adjudicated,fn);

        ExperimentMetricsReport report=metrics.regenerate(plan.planId());assertThat(report.tp()).isEqualTo(1);assertThat(report.fn()).isEqualTo(1);
        assertThat(report.recall()).isEqualTo(.5);assertThat(report.precision()).isEqualTo(1);assertThat(report.f1()).isCloseTo(2d/3d,org.assertj.core.data.Offset.offset(1e-12));
        String json=new String(exports.export(plan.planId()),StandardCharsets.UTF_8);assertThat(json).contains("EXPERIMENT_PACKAGE_SCHEMA_V1","EXPERIMENT_METRICS_V1","configurationHash").doesNotContain("api-key","OPENAI_API_KEY");
        mvc.perform(get("/experiment/evaluations")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Finding ID (opțional pentru FN/TN)")));
    }

    private static EvaluationDecisionRequest request(UUID run,String truth,DetectionClass type,String reviewer,EvaluationDecisionKind kind,List<UUID> sources){return new EvaluationDecisionRequest(run,Optional.empty(),truth,type,2,2,2,2,2,2,2,reviewer,kind,true,sources,"audit");}
    private static GroundTruthIssue truth(String id,String scenario){return new GroundTruthIssue(id,scenario,"BAD",Viewport.DESKTOP,"initial","#target","barrier","1.1.1","A",GroundTruthIssue.ExpectedPresence.PRESENT,"manual","fixture-test-"+id,"a".repeat(64));}
}
