package ro.sluta.accessibility.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ro.sluta.accessibility.analysis.ai.*;
import ro.sluta.accessibility.analysis.axe.*;
import ro.sluta.accessibility.domain.*;

public final class F5TestFixtures {
    static final UUID RUN = UUID.nameUUIDFromBytes("run".getBytes());
    static final UUID SNAPSHOT = UUID.nameUUIDFromBytes("snapshot".getBytes());

    public static AxeAnalysisResult axe(Viewport viewport, Object... selectors) {
        List<AxeNodeResult> nodes = java.util.Arrays.stream(selectors)
                .map(selector -> new AxeNodeResult(selector, "<img class=hero>", "Fix alt", "serious",
                        List.of(), List.of(), List.of())).toList();
        AxeRuleResult rule = new AxeRuleResult("image-alt", "serious", List.of("wcag111", "wcag2a"),
                "Images need text alternatives", "Image alt", "https://example.test/rule", nodes);
        return new AxeAnalysisResult(UUID.randomUUID(), RUN, SNAPSHOT, viewport, URI.create("https://example.com"),
                "a".repeat(64), "WCAG_22_AA_V1", List.of("wcag2a"), "4.13.0", "4.13.0",
                Instant.now(), Instant.now(), 10, 30_000, AxeAnalysisStatus.SUCCESS, List.of(), null, null,
                List.of(rule), List.of(), List.of(), List.of(), List.of(new ArtifactReference(
                        ArtifactType.AXE_RESULTS_JSON, "storage/runs/x/axe/axe-results.json", "application/json", 1, "a", "4.13.0")));
    }

    public static AiAnalysisResult ai(Viewport viewport, String... titleSelectorPairs) throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode(); root.put("analysisSummary", "summary"); root.putArray("limitations");
        var findings = root.putArray("findings");
        for (int i = 0; i < titleSelectorPairs.length; i += 2) {
            var item = findings.addObject(); item.put("title", titleSelectorPairs[i]); item.put("description", "description");
            item.putArray("wcagCriteria").add("1.1.1"); item.put("conformanceLevel", "A"); item.put("severity", "MODERATE");
            var element = item.putObject("element"); element.put("selector", titleSelectorPairs[i + 1]); element.put("htmlSnippet", "<img>");
            element.putNull("role"); element.putNull("accessibleName"); element.putNull("visibleText");
            item.put("explanation", "AI explanation"); item.put("impact", "AI impact"); item.put("recommendation", "AI recommendation");
            item.put("confidence", 0.8); item.putArray("evidence").add("image");
        }
        return new AiAnalysisResult(RUN, SNAPSHOT, "GPT5_NANO", "gpt-5-nano", AiCondition.AI_MULTIMODAL,
                AiRunProfile.EXPERIMENT_V1, "PROMPT_ACCESSIBILITY_V1", "AI_FINDINGS_SCHEMA_V1", "PAGE_CONTEXT_V1",
                true, root, new AiUsage(100, 0, 20, 120), new AiCost(new BigDecimal("0.000013"), false),
                new AiCost(new BigDecimal("0.004"), true), 20, 0, "resp", "req", "gpt-5-nano-test", null, null,
                List.of(new ArtifactReference(ArtifactType.AI_PARSED_JSON, "storage/runs/x/ai/parsed.json",
                        "application/json", 1, "b", "AI_FINDINGS_SCHEMA_V1")));
    }

    public static PageSnapshot snapshot(Viewport viewport) {
        return new PageSnapshot(SNAPSHOT, RUN, URI.create("https://example.com"), URI.create("https://example.com"),
                Instant.now(), viewport, null, null, null, List.of(), List.of(), SnapshotStatus.SUCCESS,
                List.of(), null, null, "f2-v1", "a".repeat(64), "none@1", List.of());
    }
    private F5TestFixtures() { }
}
