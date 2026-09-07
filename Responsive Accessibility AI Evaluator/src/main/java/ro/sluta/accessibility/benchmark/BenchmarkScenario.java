package ro.sluta.accessibility.benchmark;

public record BenchmarkScenario(String scenarioId, String title, String wcagCriterion, String conformanceLevel,
        String category, String stateId, String interactionScenarioVersion, String targetElement, String barrier,
        String manualEvidence, boolean responsiveOnly) {
    public BenchmarkScenario {
        if (scenarioId == null || !scenarioId.matches("S(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("scenarioId invalid: " + scenarioId);
        }
        if (title == null || title.isBlank() || wcagCriterion == null || wcagCriterion.isBlank()
                || conformanceLevel == null || stateId == null || targetElement == null || barrier == null) {
            throw new IllegalArgumentException("Scenariul benchmark este incomplet: " + scenarioId);
        }
    }
}
