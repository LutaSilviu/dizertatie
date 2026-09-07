package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExperimentConfigurationSnapshot(UUID configurationId, String configurationVersion,
        List<String> modelIds, Map<String, Map<String, Object>> modelCatalog, String promptVersion,
        String schemaVersion, String contextVersion, String normalizerVersion, String matcherVersion,
        String fixtureVersion, String groundTruthVersion, Map<String, String> interactionScenarioVersions,
        long randomSeed, int aiConcurrency, BigDecimal hardBudgetUsd, BigDecimal maxCostPerAiCallUsd,
        List<String> pilotScenarioIds, Instant frozenAt, String configurationHash) { }
