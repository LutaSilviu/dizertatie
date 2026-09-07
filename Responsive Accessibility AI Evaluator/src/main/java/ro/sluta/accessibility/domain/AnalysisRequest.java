package ro.sluta.accessibility.domain;

import java.util.Set;

public record AnalysisRequest(String url, Set<Viewport> viewports, Set<AnalysisMethod> methods,
                              String modelId, int repeatCount, String interactionScenarioId,
                              boolean consentAi) { }
