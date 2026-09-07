package ro.sluta.accessibility.benchmark;

import ro.sluta.accessibility.domain.GroundTruthIssue;
import ro.sluta.accessibility.domain.Viewport;

public record BenchmarkCase(String caseId, BenchmarkFixture fixture, Viewport viewport,
        GroundTruthIssue groundTruth) { }
