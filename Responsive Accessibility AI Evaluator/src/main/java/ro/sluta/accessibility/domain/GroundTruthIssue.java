package ro.sluta.accessibility.domain;

public record GroundTruthIssue(
        String groundTruthId,
        String scenarioId,
        String variant,
        Viewport viewport,
        String stateId,
        String targetElement,
        String barrier,
        String wcagCriterion,
        String conformanceLevel,
        ExpectedPresence expected,
        String manualEvidence,
        String fixtureVersion,
        String fixtureHash) {
    public enum ExpectedPresence {
        PRESENT,
        ABSENT
    }
}
