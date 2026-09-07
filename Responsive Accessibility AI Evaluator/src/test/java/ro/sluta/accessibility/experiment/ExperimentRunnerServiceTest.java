package ro.sluta.accessibility.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExperimentRunnerServiceTest {
    @Autowired ExperimentRunnerService runner;
    @Autowired ExperimentPlanRepository repository;
    @Autowired JdbcClient jdbc;

    @Test void dryRunIsDeterministicAndHasTheExactFrozenMasterCountsWithoutSideEffects() {
        ExperimentFreezeRequest request = request(20260901L, "10.00", "0.01");
        var first = runner.dryRun(request); var second = runner.dryRun(request);
        assertThat(first.browserStarted()).isFalse(); assertThat(first.apiCalled()).isFalse();
        assertThat(first.configuration().configurationHash()).isEqualTo(second.configuration().configurationHash())
                .matches("[0-9a-f]{64}");
        assertThat(first.plan()).satisfies(plan -> {
            assertThat(plan.plannedCases()).isEqualTo(48); assertThat(plan.plannedAxeRuns()).isEqualTo(48);
            assertThat(plan.plannedAiTextRuns()).isEqualTo(288); assertThat(plan.plannedAiMultimodalRuns()).isEqualTo(288);
            assertThat(plan.plannedAiCalls()).isEqualTo(576); assertThat(plan.plannedHybridResults()).isEqualTo(288);
            assertThat(plan.executions()).hasSize(912); assertThat(plan.aiConcurrency()).isEqualTo(1);
            assertThat(plan.estimatedMaxCostUsd()).isEqualByComparingTo("5.76");
        });
        assertThat(first.plan().executions()).extracting(PlannedExecution::executionKey)
                .containsExactlyElementsOf(second.plan().executions().stream().map(PlannedExecution::executionKey).toList());
        assertThat(runner.dryRun(request(7L, "10", "0.01")).plan().manifestHash())
                .isNotEqualTo(first.plan().manifestHash());
    }

    @Test void pilotIsPersistedIdempotentlyCanResumeAndEnforcesConfirmationConcurrencyAndBudget() {
        ExperimentFreezeRequest request = request(91L, "2.00", "0.01");
        ExperimentPlan first = runner.createPlan(request, ExperimentMode.PILOT, false);
        ExperimentPlan repeated = runner.createPlan(request, ExperimentMode.PILOT, false);
        assertThat(repeated.planId()).isEqualTo(first.planId());
        assertThat(repeated.executions()).hasSize(first.executions().size()).hasSize(152);
        assertThat(first.plannedCases()).isEqualTo(8); assertThat(first.plannedAxeRuns()).isEqualTo(8);
        assertThat(first.plannedAiTextRuns()).isEqualTo(48); assertThat(first.plannedAiMultimodalRuns()).isEqualTo(48);
        assertThat(first.plannedHybridResults()).isEqualTo(48);
        assertThat(jdbc.sql("select count(*) from ground_truth_issue").query(Integer.class).single()).isEqualTo(48);

        for (int index = 0; index < first.plannedAxeRuns(); index++) {
            PlannedExecution axe = runner.reserveNext(first.planId(), false).orElseThrow();
            assertThat(axe.type()).isEqualTo(ExecutionType.AXE);
            runner.complete(axe.executionId(), ExecutionStatus.SUCCESS, BigDecimal.ZERO, null, null);
        }
        assertThatThrownBy(() -> runner.reserveNext(first.planId(), false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmare plătită");
        PlannedExecution ai = runner.reserveNext(first.planId(), true).orElseThrow();
        assertThat(ai.paid()).isTrue(); assertThat(ai.reservedCostUsd()).isEqualByComparingTo("0.01");
        ExperimentPlan resumed = runner.resume(first.planId());
        assertThat(resumed.executions()).filteredOn(value -> value.status() == ExecutionStatus.RUNNING)
                .extracting(PlannedExecution::executionId).containsExactly(ai.executionId());
        assertThat(runner.reserveNext(first.planId(), true)).isEmpty();
        runner.complete(ai.executionId(), ExecutionStatus.SUCCESS, new BigDecimal("0.005"), null, null);
        assertThat(runner.resume(first.planId()).executions()).filteredOn(value -> value.executionId().equals(ai.executionId()))
                .extracting(PlannedExecution::status).containsExactly(ExecutionStatus.SUCCESS);
        assertThat(runner.reserveNext(first.planId(), true)).isPresent();
    }

    @Test void hardBudgetStopsBeforeTheFirstPaidCallAndFinalRequiresExplicitConfirmation() {
        ExperimentFreezeRequest limited = request(92L, "0.005", "0.01");
        ExperimentPlan pilot = runner.createPlan(limited, ExperimentMode.PILOT, false);
        for (int index = 0; index < pilot.plannedAxeRuns(); index++) {
            PlannedExecution axe = runner.reserveNext(pilot.planId(), false).orElseThrow();
            runner.complete(axe.executionId(), ExecutionStatus.SUCCESS, BigDecimal.ZERO, null, null);
        }
        assertThat(runner.reserveNext(pilot.planId(), true)).isEmpty();
        assertThat(repository.find(pilot.planId()).orElseThrow().status()).isEqualTo(PlanStatus.PAUSED_BUDGET);
        assertThatThrownBy(() -> runner.createPlan(request(93L, "10", "0.01"), ExperimentMode.FINAL, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("576 apeluri");
    }

    private static ExperimentFreezeRequest request(long seed, String budget, String perCall) {
        return new ExperimentFreezeRequest(seed, 1, new BigDecimal(budget), new BigDecimal(perCall), List.of("S01", "S04"));
    }
}
