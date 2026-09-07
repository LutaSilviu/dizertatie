package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentPlan(UUID planId, UUID configurationId, ExperimentMode mode, long randomSeed,
        int aiConcurrency, BigDecimal hardBudgetUsd, BigDecimal maxCostPerAiCallUsd, int plannedCases,
        int plannedAxeRuns, int plannedAiTextRuns, int plannedAiMultimodalRuns, int plannedHybridResults,
        BigDecimal estimatedMaxCostUsd, String manifestHash, PlanStatus status, Instant createdAt,
        List<PlannedExecution> executions) {
    public ExperimentPlan { executions = executions == null ? List.of() : List.copyOf(executions); }
    @com.fasterxml.jackson.annotation.JsonProperty
    public int plannedAiCalls() { return plannedAiTextRuns + plannedAiMultimodalRuns; }
}
