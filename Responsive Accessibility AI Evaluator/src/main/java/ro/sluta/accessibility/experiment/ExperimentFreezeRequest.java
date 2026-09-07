package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.util.List;

public record ExperimentFreezeRequest(long randomSeed, int aiConcurrency, BigDecimal hardBudgetUsd,
        BigDecimal maxCostPerAiCallUsd, List<String> pilotScenarioIds) {
    public ExperimentFreezeRequest {
        aiConcurrency = aiConcurrency <= 0 ? 1 : aiConcurrency;
        if (aiConcurrency != 1) throw new IllegalArgumentException("Concurența AI înghețată pentru experiment este 1.");
        hardBudgetUsd = hardBudgetUsd == null ? BigDecimal.ZERO : hardBudgetUsd;
        maxCostPerAiCallUsd = maxCostPerAiCallUsd == null ? BigDecimal.ZERO : maxCostPerAiCallUsd;
        if (hardBudgetUsd.signum() < 0 || maxCostPerAiCallUsd.signum() < 0) throw new IllegalArgumentException("Bugetele nu pot fi negative.");
        pilotScenarioIds = pilotScenarioIds == null || pilotScenarioIds.isEmpty() ? List.of("S01", "S04")
                : pilotScenarioIds.stream().map(String::toUpperCase).sorted().distinct().toList();
    }
}
