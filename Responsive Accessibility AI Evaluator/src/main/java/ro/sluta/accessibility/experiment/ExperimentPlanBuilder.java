package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.benchmark.BenchmarkCase;
import ro.sluta.accessibility.benchmark.BenchmarkCatalog;
import ro.sluta.accessibility.browser.AtomicArtifactStore;

@Component
public class ExperimentPlanBuilder {
    private final BenchmarkCatalog benchmark;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public ExperimentPlanBuilder(BenchmarkCatalog benchmark) { this(benchmark, Clock.systemUTC()); }
    ExperimentPlanBuilder(BenchmarkCatalog benchmark, Clock clock) { this.benchmark = benchmark; this.clock = clock; }

    public ExperimentPlan build(ExperimentConfigurationSnapshot configuration, ExperimentMode mode) {
        List<BenchmarkCase> cases = benchmark.cases().stream().filter(value -> mode != ExperimentMode.PILOT
                || configuration.pilotScenarioIds().contains(value.fixture().scenario().scenarioId())).toList();
        if (mode == ExperimentMode.PILOT && cases.isEmpty()) throw new IllegalArgumentException("Pilotul nu conține cazuri aprobate.");
        UUID planId = UUID.nameUUIDFromBytes((configuration.configurationId() + "|" + mode).getBytes(StandardCharsets.UTF_8));
        List<Seed> axe = new ArrayList<>(), ai = new ArrayList<>(), hybrid = new ArrayList<>();
        for (BenchmarkCase benchmarkCase : cases) {
            axe.add(new Seed(benchmarkCase.caseId(), ExecutionType.AXE, null, null));
            for (String model : configuration.modelIds()) for (int repetition = 1; repetition <= 3; repetition++) {
                ai.add(new Seed(benchmarkCase.caseId(), ExecutionType.AI_TEXT, model, repetition));
                ai.add(new Seed(benchmarkCase.caseId(), ExecutionType.AI_MULTIMODAL, model, repetition));
                hybrid.add(new Seed(benchmarkCase.caseId(), ExecutionType.HYBRID, model, repetition));
            }
        }
        shuffle(axe, configuration.randomSeed() ^ 0x41A3L); shuffle(ai, configuration.randomSeed() ^ 0xA17EL);
        shuffle(hybrid, configuration.randomSeed() ^ 0x48B1DL);
        List<Seed> ordered = new ArrayList<>(axe); ordered.addAll(ai); ordered.addAll(hybrid);
        List<PlannedExecution> executions = new ArrayList<>(); int order = 0;
        for (Seed seed : ordered) {
            String key = seed.caseId + "|" + seed.type + "|" + text(seed.modelId) + "|" + text(seed.repetition);
            UUID executionId = UUID.nameUUIDFromBytes((planId + "|" + key).getBytes(StandardCharsets.UTF_8));
            executions.add(new PlannedExecution(executionId, planId, order++, key, seed.caseId, seed.type,
                    seed.modelId, seed.repetition, ExecutionStatus.PLANNED, BigDecimal.ZERO, BigDecimal.ZERO, null, null));
        }
        int axeCount = axe.size(), textCount = (int) ai.stream().filter(value -> value.type == ExecutionType.AI_TEXT).count();
        int multimodalCount = ai.size() - textCount, hybridCount = hybrid.size();
        BigDecimal maxCost = configuration.maxCostPerAiCallUsd().multiply(BigDecimal.valueOf(textCount + multimodalCount));
        String manifestHash = AtomicArtifactStore.sha256(executions.stream().map(PlannedExecution::executionKey)
                .collect(java.util.stream.Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8));
        if (mode != ExperimentMode.PILOT && (cases.size() != 48 || axeCount != 48 || textCount != 288
                || multimodalCount != 288 || hybridCount != 288)) throw new IllegalStateException("Planul final nu respectă matricea înghețată.");
        return new ExperimentPlan(planId, configuration.configurationId(), mode, configuration.randomSeed(),
                configuration.aiConcurrency(), configuration.hardBudgetUsd(), configuration.maxCostPerAiCallUsd(), cases.size(),
                axeCount, textCount, multimodalCount, hybridCount, maxCost, manifestHash, PlanStatus.PLANNED,
                Instant.now(clock), executions);
    }

    private static <T> void shuffle(List<T> values, long seed) { Collections.shuffle(values, new Random(seed)); }
    private static String text(Object value) { return value == null ? "-" : value.toString(); }
    private record Seed(String caseId, ExecutionType type, String modelId, Integer repetition) { }
}
