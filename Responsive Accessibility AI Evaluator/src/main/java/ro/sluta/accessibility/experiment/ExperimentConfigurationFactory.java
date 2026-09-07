package ro.sluta.accessibility.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.benchmark.BenchmarkCatalog;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.config.AiAnalysisProperties;
import ro.sluta.accessibility.normalization.FindingNormalizerV1;
import ro.sluta.accessibility.normalization.HybridFindingCombinerV1;

@Component
public class ExperimentConfigurationFactory {
    public static final String CONFIGURATION_VERSION = "EXPERIMENT_CONFIGURATION_V1";
    private final AiAnalysisProperties ai;
    private final BenchmarkCatalog benchmark;
    private final ObjectMapper mapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ExperimentConfigurationFactory(AiAnalysisProperties ai, BenchmarkCatalog benchmark, ObjectMapper mapper) {
        this(ai, benchmark, mapper, Clock.systemUTC());
    }

    ExperimentConfigurationFactory(AiAnalysisProperties ai, BenchmarkCatalog benchmark, ObjectMapper mapper, Clock clock) {
        this.ai = ai; this.benchmark = benchmark; this.mapper = mapper; this.clock = clock;
    }

    public ExperimentConfigurationSnapshot freeze(ExperimentFreezeRequest request) {
        var models = ai.models().entrySet().stream().filter(entry -> entry.getValue().enabled())
                .sorted(Map.Entry.comparingByKey()).toList();
        if (models.size() != 2) throw new IllegalStateException("Experimentul principal cere exact două modele AI active.");
        Map<String, Map<String, Object>> modelCatalog = new LinkedHashMap<>();
        for (var entry : models) {
            var value = entry.getValue(); Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("providerModel", value.providerModel()); fields.put("modalities", value.modalities());
            fields.put("acceptedParameters", value.acceptedParameters()); fields.put("inputPricePerMillion", value.inputPricePerMillion());
            fields.put("cachedInputPricePerMillion", value.cachedInputPricePerMillion()); fields.put("outputPricePerMillion", value.outputPricePerMillion());
            fields.put("effectiveFrom", value.effectiveFrom()); modelCatalog.put(entry.getKey(), Map.copyOf(fields));
        }
        Map<String, String> interactions = new LinkedHashMap<>();
        benchmark.scenarios().forEach(value -> interactions.put(value.scenarioId(), value.interactionScenarioVersion()));
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("version", CONFIGURATION_VERSION); identity.put("models", modelCatalog);
        identity.put("promptVersion", ai.promptVersion()); identity.put("schemaVersion", ai.schemaVersion());
        identity.put("contextVersion", ai.contextVersion()); identity.put("normalizerVersion", FindingNormalizerV1.VERSION);
        identity.put("matcherVersion", HybridFindingCombinerV1.VERSION); identity.put("fixtureVersion", BenchmarkCatalog.FIXTURE_VERSION);
        identity.put("groundTruthVersion", BenchmarkCatalog.GROUND_TRUTH_VERSION); identity.put("interactions", interactions);
        identity.put("randomSeed", request.randomSeed()); identity.put("aiConcurrency", request.aiConcurrency());
        identity.put("hardBudgetUsd", request.hardBudgetUsd()); identity.put("maxCostPerAiCallUsd", request.maxCostPerAiCallUsd());
        identity.put("pilotScenarioIds", request.pilotScenarioIds());
        String hash = hash(identity); UUID id = UUID.nameUUIDFromBytes(("experiment-configuration:" + hash).getBytes(StandardCharsets.UTF_8));
        return new ExperimentConfigurationSnapshot(id, CONFIGURATION_VERSION, models.stream().map(Map.Entry::getKey).toList(),
                Map.copyOf(modelCatalog), ai.promptVersion(), ai.schemaVersion(), ai.contextVersion(),
                FindingNormalizerV1.VERSION, HybridFindingCombinerV1.VERSION, BenchmarkCatalog.FIXTURE_VERSION,
                BenchmarkCatalog.GROUND_TRUTH_VERSION, Map.copyOf(interactions), request.randomSeed(), request.aiConcurrency(),
                request.hardBudgetUsd(), request.maxCostPerAiCallUsd(), request.pilotScenarioIds(), Instant.now(clock), hash);
    }

    private String hash(Object value) {
        try { return AtomicArtifactStore.sha256(mapper.writeValueAsBytes(value)); }
        catch (Exception exception) { throw new IllegalStateException("Configurația experimentului nu poate fi serializată.", exception); }
    }
}
