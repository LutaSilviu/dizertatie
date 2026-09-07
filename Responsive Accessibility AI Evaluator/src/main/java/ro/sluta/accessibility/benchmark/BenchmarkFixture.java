package ro.sluta.accessibility.benchmark;

public record BenchmarkFixture(BenchmarkScenario scenario, BenchmarkVariant variant, String fixtureVersion,
        String fixtureHash, String groundTruthVersion) {
    public String fixtureId() { return scenario.scenarioId() + "-" + variant; }
}
