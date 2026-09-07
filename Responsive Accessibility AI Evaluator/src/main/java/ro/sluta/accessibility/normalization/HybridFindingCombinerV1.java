package ro.sluta.accessibility.normalization;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.analysis.ai.AiCondition;
import ro.sluta.accessibility.domain.*;

@Component
public class HybridFindingCombinerV1 {
    public static final String VERSION = "HYBRID_MATCHER_V1";
    public static final double THRESHOLD = 0.75;

    public HybridResult combine(String caseId, String modelId, int repetition, AiCondition condition,
            List<PredictedFinding> axeFindings, List<PredictedFinding> aiFindings) {
        if (condition != AiCondition.AI_MULTIMODAL)
            throw new IllegalArgumentException("Hibridul principal acceptă numai AI_MULTIMODAL.");
        List<PredictedFinding> axe = axeFindings.stream().filter(f -> f.source() == FindingSource.AXE_ONLY)
                .sorted(Comparator.comparing(PredictedFinding::findingKey)).toList();
        List<PredictedFinding> ai = aiFindings.stream().filter(f -> f.source() == FindingSource.AI_ONLY)
                .sorted(Comparator.comparing(PredictedFinding::findingKey)).toList();
        Set<UUID> usedAi = new HashSet<>();
        List<PredictedFinding> output = new ArrayList<>();
        List<AmbiguousMatch> ambiguous = new ArrayList<>();

        for (PredictedFinding axeFinding : axe) {
            List<Candidate> candidates = ai.stream().filter(candidate -> !usedAi.contains(candidate.findingId()))
                    .filter(candidate -> sameUnit(axeFinding, candidate))
                    .filter(candidate -> intersects(axeFinding.wcagCriteria(), candidate.wcagCriteria()))
                    .map(candidate -> new Candidate(candidate, evidence(axeFinding.location(), candidate.location())))
                    .filter(candidate -> candidate.evidence().totalScore() >= THRESHOLD)
                    .sorted(Comparator.comparingDouble((Candidate value) -> value.evidence().totalScore()).reversed()
                            .thenComparing(value -> value.finding().findingKey())).toList();
            if (candidates.isEmpty()) { output.add(axeFinding); continue; }
            Candidate best = candidates.getFirst();
            if (candidates.size() > 1 && Math.abs(best.evidence().totalScore() - candidates.get(1).evidence().totalScore()) < 0.000001) {
                ambiguous.add(new AmbiguousMatch(axeFinding.findingId(), candidates.stream()
                        .filter(value -> Math.abs(best.evidence().totalScore() - value.evidence().totalScore()) < 0.000001)
                        .map(value -> value.finding().findingId()).toList(), best.evidence().totalScore(), VERSION));
                output.add(axeFinding);
                continue;
            }
            usedAi.add(best.finding().findingId());
            output.add(merge(caseId, modelId, repetition, axeFinding, best.finding(), best.evidence()));
        }
        ai.stream().filter(finding -> !usedAi.contains(finding.findingId())).forEach(output::add);
        output.sort(Comparator.comparing((PredictedFinding f) -> f.viewport().ordinal()).thenComparing(PredictedFinding::findingKey));
        return new HybridResult(caseId, modelId, repetition, condition, VERSION, List.copyOf(output),
                List.copyOf(ambiguous), 0);
    }

    private PredictedFinding merge(String caseId, String modelId, int repetition, PredictedFinding axe,
            PredictedFinding ai, LocationMatchEvidence evidence) {
        Set<String> criteria = new LinkedHashSet<>(axe.wcagCriteria()); criteria.addAll(ai.wcagCriteria());
        List<String> sortedCriteria = criteria.stream().sorted(Comparator.comparing(FindingNormalizerV1::criterionParts,
                java.util.Arrays::compare)).toList();
        List<FindingSourceReference> refs = new ArrayList<>(axe.sourceRefs()); refs.addAll(ai.sourceRefs());
        List<String> explanations = new ArrayList<>(axe.explanations());
        ai.explanations().stream().filter(value -> !explanations.contains(value)).forEach(explanations::add);
        CanonicalSeverity display = CanonicalSeverity.maximum(axe.axeSeverity(), ai.aiSeverity());
        String key = FindingNormalizerV1.hash(VERSION + "|" + caseId + "|" + modelId + "|" + repetition
                + "|" + axe.findingKey() + "|" + ai.findingKey());
        return new PredictedFinding(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), axe.runId(),
                axe.snapshotId(), axe.viewport(), FindingSource.BOTH, refs, axe.title(), axe.description(),
                sortedCriteria.isEmpty() ? null : sortedCriteria.getFirst(), sortedCriteria, display,
                axe.axeSeverity(), ai.aiSeverity(), display, strongerLevel(axe.conformanceLevel(), ai.conformanceLevel()),
                axe.normalizedCategory(), axe.elementFingerprint(), prefer(axe.location(), ai.location()), explanations,
                join(axe.impact(), ai.impact()), join(axe.recommendation(), ai.recommendation()), ai.confidence(),
                axe.rawReference() + " | " + ai.rawReference(), key,
                axe.duplicateCount() + ai.duplicateCount(), evidence, FindingNormalizerV1.VERSION);
    }

    LocationMatchEvidence evidence(FindingLocation axe, FindingLocation ai) {
        List<String> reasons = new ArrayList<>();
        String axeSelector = normalized(axe.selector()), aiSelector = normalized(ai.selector());
        if (!axeSelector.isBlank() && axeSelector.equals(aiSelector)) {
            return new LocationMatchEvidence(1, 0, 0, 0, 0, 1, List.of("selector-identic"), VERSION);
        }
        double selector = suffixSimilarity(axeSelector, aiSelector) ? 0.20 : 0;
        double role = equal(axe.role(), ai.role()) ? 0.20 : 0;
        double name = equal(axe.accessibleName(), ai.accessibleName()) ? 0.30 : 0;
        double text = equal(axe.visibleText(), ai.visibleText()) ? 0.20 : 0;
        double dom = proximity(axe, ai) ? 0.10 : 0;
        if (selector > 0) reasons.add("selector-similar"); if (role > 0) reasons.add("role-identic");
        if (name > 0) reasons.add("accessibleName-identic"); if (text > 0) reasons.add("visibleText-identic");
        if (dom > 0) reasons.add("proximitate-dom");
        return new LocationMatchEvidence(selector, role, name, text, dom,
                round(selector + role + name + text + dom), reasons, VERSION);
    }

    private static boolean sameUnit(PredictedFinding a, PredictedFinding b) {
        return a.snapshotId().equals(b.snapshotId()) && a.viewport() == b.viewport();
    }
    private static boolean intersects(List<String> first, List<String> second) {
        return first.stream().anyMatch(second::contains);
    }
    private static boolean equal(String first, String second) {
        String a = normalized(first), b = normalized(second); return !a.isBlank() && a.equals(b);
    }
    private static String normalized(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    private static boolean suffixSimilarity(String first, String second) {
        if (first.isBlank() || second.isBlank()) return false;
        String a = first.replaceAll(".*[ >]", ""), b = second.replaceAll(".*[ >]", "");
        return a.equals(b);
    }
    private static boolean proximity(FindingLocation first, FindingLocation second) {
        String a = normalized(first.domPath()), b = normalized(second.domPath());
        if (a.isBlank() || b.isBlank()) return false;
        int prefix = 0; while (prefix < Math.min(a.length(), b.length()) && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        return prefix >= Math.min(a.length(), b.length()) * 0.6;
    }
    private static FindingLocation prefer(FindingLocation axe, FindingLocation ai) {
        return new FindingLocation(first(axe.selector(), ai.selector()), first(axe.htmlSnippet(), ai.htmlSnippet()),
                first(ai.role(), axe.role()), first(ai.accessibleName(), axe.accessibleName()),
                first(ai.visibleText(), axe.visibleText()), first(axe.domPath(), ai.domPath()));
    }
    private static String first(String first, String second) { return first == null || first.isBlank() ? second : first; }
    private static String join(String first, String second) {
        if (first == null || first.isBlank()) return second; if (second == null || second.isBlank() || first.equals(second)) return first;
        return first + "\n\nAI: " + second;
    }
    private static CanonicalConformanceLevel strongerLevel(CanonicalConformanceLevel first, CanonicalConformanceLevel second) {
        if (first == CanonicalConformanceLevel.AA || second == CanonicalConformanceLevel.AA) return CanonicalConformanceLevel.AA;
        if (first == CanonicalConformanceLevel.A || second == CanonicalConformanceLevel.A) return CanonicalConformanceLevel.A;
        return CanonicalConformanceLevel.UNKNOWN;
    }
    private static double round(double value) { return Math.round(value * 1000d) / 1000d; }

    private record Candidate(PredictedFinding finding, LocationMatchEvidence evidence) { }
    public record AmbiguousMatch(UUID axeFindingId, List<UUID> aiFindingIds, double score, String matcherVersion) { }
    public record HybridResult(String caseId, String modelId, int repetition, AiCondition condition,
            String matcherVersion, List<PredictedFinding> findings, List<AmbiguousMatch> ambiguousMatches,
            int additionalAiCalls) { }
}
